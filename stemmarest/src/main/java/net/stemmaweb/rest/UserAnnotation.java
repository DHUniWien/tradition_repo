package net.stemmaweb.rest;

import java.util.*;

import javax.ws.rs.*;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import net.stemmaweb.model.UserNodeModel;
import net.stemmaweb.services.DatabaseService;
import org.neo4j.graphdb.*;


/**
 * Comprises all the API calls related to a user.
 * Can be called using http://BASE_URL/userobject
 * @author SKFM 2016/02/01
 */

public class UserAnnotation {
    private GraphDatabaseService db;
    private Node userNode;

    private static final String PROP_LABEL = "__LABEL__";

    private enum UADataTypes {
        BOOL,
        BOOLARRAY,
        LONG,
        LONGARRAY,
        DOUBLE,
        DOUBLEARRAY,
        STRING,
        STRINGARRAY
    }

    UserAnnotation(Node userNode) {
        this.userNode = userNode;
        this.db = userNode.getGraphDatabase();
    }

    private boolean isValidDataType(String dataType)
    {
        try {
            UADataTypes.valueOf(dataType);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return true;
    }

    private boolean isSystemProperty(String property)
    {
        return property.startsWith("__") && property.endsWith("__");
    }

    private Boolean validTypeForProperty(Object value, UADataTypes type) {
        switch (type) {
            case BOOL:
                return value.getClass().equals(Boolean.class);
            case BOOLARRAY:
                return value.getClass().equals(Boolean[].class);
            case DOUBLE:
                return value.getClass().equals(Double.class);
            case DOUBLEARRAY:
                return value.getClass().equals(Double[].class);
            case LONG:
                return value.getClass().equals(Long.class);
            case LONGARRAY:
                return value.getClass().equals(Long[].class);
            case STRING:
                return value.getClass().equals(String.class);
            case STRINGARRAY:
                return value.getClass().equals(String[].class);
        }
        return false;
    }

    private void setPropertyValues(Node userdefNode, LinkedHashMap<String, String> definition) {
        Set<String> objKeys = definition.keySet();
        for (String k : objKeys) {
            if (!isSystemProperty(k)) {
                String dataType = definition.get(k);
                if (isValidDataType(dataType)) {
                    userdefNode.setProperty(k, dataType);
                }
            }
        }
    }

    private Node findUserDefinitionNode(ERelations entityType, String label) {
        Node systemNode = null;
        for(Node curNode : DatabaseService.getRelated(userNode, entityType)) {
            if (curNode.getProperty(PROP_LABEL).toString().equals(label)) {
                systemNode = curNode;
                break;
            }
        }
        return systemNode;
    }
    /**
     * Creates a SYSTEM-Object based on the parameters submitted in JSON.
     *
     * @param label -  in JSON format
     * @param definition -  the node's definition (property, data type) in JSON format.
     *                   For example: {"name": "STRING", "year": "LONG"}
     * @return A JSON UserModel or a JSON error message
     */
    @PUT
    @Path("/define/entity/{label}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response defineEntity(@PathParam("label") String label, LinkedHashMap<String, String> definition) {

        Status returnval = Status.CREATED;
        Node systemNode;
        try (Transaction tx = db.beginTx()) {
            // Is there a defined entity that we are replacing? Find it
            systemNode = findUserDefinitionNode(ERelations.USER_ENTITY, label);
            // or else create it.
            if (systemNode == null) {
                systemNode = db.createNode(Nodes.USERENTITY);
                userNode.createRelationshipTo(systemNode, ERelations.USER_ENTITY);
            } else
                returnval = Status.OK;

            // Now clear out and reset all its properties.
            systemNode.getPropertyKeys().forEach(systemNode::removeProperty);
            systemNode.setProperty(PROP_LABEL, label);
            setPropertyValues(systemNode, definition);

            tx.success();
        } catch (Exception e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.status(returnval).entity(new UserNodeModel(systemNode)).build();
    }

    // @PUT @PATH("/define/relationship/{label}
    @PUT
    @Path("/define/relationship/{label}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response defineRelationship(@PathParam("label") String label, LinkedHashMap<String, String> definition) {

        Status returnval = Status.CREATED;
        Node systemNode;
        try (Transaction tx = db.beginTx()) {
            // Is there a defined entity that we are replacing? Find it
            systemNode = findUserDefinitionNode(ERelations.USER_RELATIONSHIP, label);
            // or else create it.
            if (systemNode == null) {
                systemNode = db.createNode(Nodes.USERREL);
                userNode.createRelationshipTo(systemNode, ERelations.USER_RELATIONSHIP);
            } else
                returnval = Status.OK;

            // Now clear out and reset all its properties.
            systemNode.getPropertyKeys().forEach(systemNode::removeProperty);
            systemNode.setProperty(PROP_LABEL, label);
            setPropertyValues(systemNode, definition);

            tx.success();
        } catch (Exception e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.status(returnval).entity(new UserNodeModel(systemNode)).build();
    }

    private String setAnnotationProperties(Node systemNode, Node node, LinkedHashMap<String, Object> data) {
        String label = systemNode.getProperty(PROP_LABEL).toString();
        for (String property : data.keySet()) {
            if (!systemNode.hasProperty(property))
                return "Property " + property + " not valid for entities of label " + label;
            if (isSystemProperty(property))
                return "Property name " + property + " reserved for system use";
            UADataTypes valueType = UADataTypes.valueOf(systemNode.getProperty(property).toString());
            Object value = data.get(property);
            if (!validTypeForProperty(value, valueType))
                return "Value for " + property + " must be of type " + valueType.toString();
            // If we've passed all these checks, we can set the property.
            node.setProperty(property, value);
        }
        return "OK";
    }
    /**
     * Creates a user-object of type <obj-type> based on the parameters submitted in JSON.
     *
     * @param label -  the node's label
     * @param data -  in JSON format
     * @return OK if successful, otherwise an error message message
     */
    @POST
    @Path("/create/entity/{label}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(@PathParam("label") String label, LinkedHashMap<String, Object> data) {
        if (data == null) return Response.status(Status.BAD_REQUEST).build();

        try (Transaction tx = db.beginTx()) {
            Node systemNode = findUserDefinitionNode(ERelations.USER_ENTITY, label);
            if (systemNode == null)
                return Response.status(Status.NOT_FOUND).entity("No user definition found for " + label).build();

            Node node = db.createNode(DynamicLabel.label(label));
            systemNode.createRelationshipTo(node, ERelations.INSTANCE_OF);
            String setResult = setAnnotationProperties(systemNode, node, data);
            if (!setResult.equals("OK"))
                return Response.status(Status.BAD_REQUEST).entity(setResult).build();
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.status(Response.Status.CREATED).build();
    }
    // @PUT @PATH("/link/{entityID}

    // @PUT @PATH("/create/relationship/{label}/from/{n1}/to/{n2}") + optional properties map

    /**
     * updates a user-object based on the parameters submitted in JSON.
     *
     * @param label - the node's label
     * @param property - the searched property
     * @param propertyValue - the property's value
     * @param data -  in JSON format
     * @return OK or an error message
     */
    @PUT
    @Path("/update/{label}/where/{property}/is/{value}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response update(@PathParam("label") String label,
                           @PathParam("property") String property,
                           @PathParam("value") String propertyValue,
                           LinkedHashMap<String, Object> data) {

        if (label == null || property == null || data == null) {
            return Response.status(Status.NOT_FOUND).build();
        }
        try (Transaction tx = db.beginTx()) {
            Node systemNode = findUserDefinitionNode(ERelations.USER_ENTITY, label);
            if (systemNode == null)
                return Response.status(Status.NOT_FOUND).entity("No user definition found for " + label).build();

            // TODO rewrite this

            /* Set<String> objDataKeys = data.keySet();
            Object propValue = str2obj(propertyValue, (String)systemNode.getProperty(property));
            Iterator objNodes = db.findNodes(DynamicLabel.label(label), property, propValue);
            while (objNodes.hasNext()) {
                Node curNode = (Node)objNodes.next();
                Iterable<String> sysProperties = systemNode.getPropertyKeys();
                for(String curProperty: sysProperties) {
                    if(!isSystemProperty(curProperty)) {
                        if(objDataKeys.contains(curProperty)) {
                            propValue = str2obj(data.get(curProperty), (String)systemNode.getProperty(curProperty));
                            curNode.setProperty(curProperty, propValue);
                        } else {
                            try {
                                curNode.removeProperty(curProperty);
                            } catch (org.neo4j.graphdb.NotFoundException e) {}
                        }
                    }
                }
            } */
            tx.success();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.status(Status.OK).build();
    }

    /**
     * gets a list of nodes with the given label.
     *
     * @param label - the searched label
     * @return A list of JSON Node-objects or a JSON error message
     */
    @GET
    @Path("/getentities/{label}")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response get(@PathParam("label") String label) {
        if (label == null) {
            return Response.status(Status.NOT_FOUND).build();
        }
        ArrayList<UserNodeModel> resultList = new ArrayList<>();
        try (Transaction tx = db.beginTx()) {
            Node systemNode = findUserDefinitionNode(ERelations.USER_ENTITY, label);
            if(systemNode == null) {
                return Response.status(Status.NOT_FOUND).build();
            }
            DatabaseService.getRelated(systemNode, ERelations.INSTANCE_OF)
                    .forEach(x -> resultList.add(new UserNodeModel(x)));

            tx.success();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.ok(resultList).build();
    }

    private Boolean valueEquals(Node systemNode, String property, Object propertyVal, String jsonVal) {
        UADataTypes type = UADataTypes.valueOf(systemNode.getProperty(property).toString());
        String[] arrElements = {};
        if (type.toString().endsWith("ARRAY")) {
            arrElements = jsonVal.replaceAll("\\[\\s+", "").replaceAll("\\s+]", "").split("\\s+,\\s+");
        }
        try {
            switch (type) {
                case BOOL:
                    return propertyVal.equals(Boolean.valueOf(jsonVal));
                case BOOLARRAY:
                    Boolean[] parseBool = new Boolean[arrElements.length];
                    for (int i = 0; i < arrElements.length; i++) {
                        parseBool[i] = Boolean.valueOf(arrElements[i]);
                    }
                    return propertyVal.equals(parseBool);
                case DOUBLE:
                    return propertyVal.equals(Double.valueOf(jsonVal));
                case DOUBLEARRAY:
                    Double[] parseDouble = new Double[arrElements.length];
                    for (int i = 0; i < arrElements.length; i++) {
                        parseDouble[i] = Double.valueOf(arrElements[i]);
                    }
                    return propertyVal.equals(parseDouble);
                case LONG:
                    return propertyVal.equals(Long.valueOf(jsonVal));
                case LONGARRAY:
                    Long[] parseLong = new Long[arrElements.length];
                    for (int i = 0; i < arrElements.length; i++) {
                        parseLong[i] = Long.valueOf(arrElements[i]);
                    }
                    return propertyVal.equals(parseLong);
                case STRING:
                    return propertyVal.equals(jsonVal);
                case STRINGARRAY:
                    return propertyVal.equals(arrElements);
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    /**
     * gets a node-object.
     *
     * @param label - the node's label
     * @param property - the searched property
     * @param value - the searched property's value
     * @return A JSON Node-Object or a JSON error message
     */
    @GET
    @Path("/getentities/{label}/where/{property}/is/{value}")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response get(@PathParam("label") String label,
                        @PathParam("property") String property,
                        @PathParam("value") String value) {
        if (label == null) {
            return Response.status(Status.NOT_FOUND).build();
        }
        ArrayList<UserNodeModel> resultList = new ArrayList<>();
        try (Transaction tx = db.beginTx()) {
            Node systemNode = findUserDefinitionNode(ERelations.USER_ENTITY, label);
            if (systemNode == null) {
                return Response.status(Status.NOT_FOUND).build();
            }
            final Node sn = systemNode;
            DatabaseService.getRelated(systemNode, ERelations.INSTANCE_OF).stream()
                    .filter(x -> x.hasProperty(property)
                            && valueEquals(sn, property, x.getProperty(property), value))
                    .forEach(x -> resultList.add(new UserNodeModel(x)));

            tx.success();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.ok(resultList).build();

    }

}
