package net.stemmaweb.rest;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import javax.ws.rs.*;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.xml.stream.XMLStreamException;

import com.sun.jersey.core.header.FormDataContentDisposition;
import com.sun.jersey.multipart.FormDataParam;
import net.stemmaweb.model.TraditionModel;
import net.stemmaweb.model.UserModel;
import net.stemmaweb.services.DatabaseService;
import net.stemmaweb.services.GraphDatabaseServiceProvider;

import org.checkerframework.checker.oigj.qual.O;
import org.neo4j.graphdb.*;
import org.neo4j.unsafe.impl.batchimport.cache.idmapping.string.Radix;
import org.json.simple.JSONObject;


/**
 * Comprises all the API calls related to a user.
 * Can be called using http://BASE_URL/userobject
 * @author SKFM 2016/02/01
 */

public class UserNode {
    private GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
    private GraphDatabaseService db = dbServiceProvider.getDatabase();
    public java.util.List<java.util.Map.Entry<String,Integer>> pairList= new java.util.ArrayList<>();

    private static final String PROP_SYS_TYPE = "__SYS_TYPE__";
    private static final String PROP_LABEL = "__LABEL__";

    private static final String SYS_TYPE = "USER_NODE";

    private static final String DTYPE_BOOL = "BOOL";
    private static final String DTYPE_INTEGER = "INTEGER";
    private static final String DTYPE_LONG = "LONG";
    private static final String DTYPE_STRING = "STRING";
    private static final String DTYPE_ARRAY_BOOL = "[BOOL]";
    private static final String DTYPE_ARRAY_INTEGER = "[INTEGER]";
    private static final String DTYPE_ARRAY_LONG = "[LONG]";
    private static final String DTYPE_ARRAY_STRING = "[STRING]";

    public UserNode() {
        GraphDatabaseServiceProvider dbServiceProvider = new GraphDatabaseServiceProvider();
        db = dbServiceProvider.getDatabase();
    }

    private boolean isValidObjectType(String objectType)
    {
        return objectType.equals(SYS_TYPE);
    }

    private boolean isValidDataType(String dataType)
    {
        switch (dataType) {
            case DTYPE_STRING:
            case DTYPE_INTEGER:
            case DTYPE_LONG:
            case DTYPE_BOOL:
                return true;
        }
        return false;
    }
    private boolean isSystemProperty(String property)
    {
        try {
            if (property.startsWith("__") && property.endsWith("__")) {
                return true;
            }
        } catch (NullPointerException e) {}
        return false;
    }

    private Object str2obj(Object value, String type)
    {
        if (value != null && type != null) {
            switch (type) {
                case DTYPE_STRING:
                    return (String)value;
                case DTYPE_INTEGER:
                    return (Integer)value;  // Integer.parseInt(value);
                case DTYPE_LONG:
                    return (Long)value;     //Long.parseLong(value);
                case DTYPE_BOOL:
                    return (Boolean)value;  //Boolean.parseBoolean(value);
            }
        }
        return null;
    }

    /**
     * Creates a SYSTEM-Object based on the parameters submitted in JSON.
     *
     * @param label -  in JSON format
     * @param definition -  the node's definition (property, data type) in JSON format
     * @return A JSON UserModel or a JSON error message
     */
    @PUT
    @Path("/define/{label}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response define(@PathParam("label") String label, LinkedHashMap<String, String> definition) {

        try (Transaction tx = db.beginTx()) {
            ResourceIterator<Node> sysNodes = db.findNodes(Nodes.__SYSTEM__, PROP_LABEL, label);
            while(sysNodes.hasNext()) {
                Node curNode = sysNodes.next();
                if (curNode.getProperty(PROP_SYS_TYPE).equals(SYS_TYPE)) {
                    curNode.delete();    // temp only
                    tx.success();        // temp only
                    // return Response.status(Response.Status.CONFLICT).build();
                    break;
                }
            }
            Node systemNode = db.createNode(Nodes.__SYSTEM__);
            systemNode.setProperty(PROP_SYS_TYPE, SYS_TYPE);
            systemNode.setProperty(PROP_LABEL, label);

            Set<String> objKeys = definition.keySet();
            for (String k : objKeys) {
                if (!isSystemProperty(k)) {
                    String dataType = definition.get(k);
                    if (isValidDataType(dataType)) {
                        systemNode.setProperty(k, dataType);
                    }
                }
            }
            tx.success();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.status(Response.Status.CREATED).build();
        //return Response.status(Response.Status.CREATED).entity(userModel).build();
    }

    /**
     * Creates a user-object of type <obj-type> based on the parameters submitted in JSON.
     *
     * @param label -  the node's label
     * @param data -  in JSON format
     * @return OK if successful, otherwise an error message message
     */
    @PUT
    @Path("/create/{label}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(@PathParam("label") String label, LinkedHashMap<String, Object> data) {

        if (label == null ||  data == null) {
            return Response.status(Status.NOT_FOUND).build();
        }
        try (Transaction tx = db.beginTx()) {
            Node systemNode = null;
            ResourceIterator<Node> sysNodes = db.findNodes(Nodes.__SYSTEM__, PROP_LABEL, label);
            while(sysNodes.hasNext()) {
                Node curNode = sysNodes.next();
                if (curNode.getProperty(PROP_SYS_TYPE).equals(SYS_TYPE)) {
                    systemNode = curNode;
                    break;
                }
            }
            if (systemNode == null) {
                return Response.status(Status.NOT_FOUND).build();
            }

            Iterable<String> objProperties = systemNode.getPropertyKeys();
            Node node = db.createNode(Label.label(label));

            for (String property : objProperties) {
                if(!isSystemProperty(property)) {
                    String valueType = (String)systemNode.getProperty(property);
                    Object value = str2obj(data.get(property), valueType);
                    node.setProperty(property, value);
                }
            }
            tx.success();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.status(Response.Status.CREATED).build();
    }

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
            Node systemNode = null;
            ResourceIterator<Node> sysNodes = db.findNodes(Nodes.__SYSTEM__, PROP_LABEL, label);
            while(sysNodes.hasNext()) {
                Node curNode = sysNodes.next();
                if (curNode.getProperty(PROP_SYS_TYPE).equals(SYS_TYPE)) {
                    systemNode = curNode;
                    break;
                }
            }
            if (systemNode == null) {
                return Response.status(Status.NOT_FOUND).build();
            }

            Set<String> objDataKeys = data.keySet();
            Object propValue = str2obj(propertyValue, (String)systemNode.getProperty(property));
            Iterator objNodes = db.findNodes(Label.label(label), property, propValue);
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
            }
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
    @Path("/get/{label}")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response get(@PathParam("label") String label) {

        if (label == null) {
            return Response.status(Status.NOT_FOUND).build();
        }
        ArrayList<JSONObject> resultList = new ArrayList();
        try (Transaction tx = db.beginTx()) {
            Node systemNode = db.findNode(Nodes.__SYSTEM__, PROP_LABEL, label);
            if(systemNode == null) {
                return Response.status(Status.NOT_FOUND).build();
            }
            Iterator objNodes = db.findNodes(Label.label(label));
            while (objNodes.hasNext()) {
                JSONObject jsonObj = new JSONObject();
                Node curNode = (Node)objNodes.next();
                Iterable<String> sysProperties = systemNode.getPropertyKeys();
                for(String curProperty: sysProperties) {
                    if(!isSystemProperty(curProperty)) {
                        try {
                            jsonObj.put(curProperty, curNode.getProperty(curProperty));
                        } catch (org.neo4j.graphdb.NotFoundException e) {}
                    }
                }
                resultList.add(jsonObj);
            }
            tx.success();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.ok(resultList).build();
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
    @Path("/get/{label}/where/{property}/is/{value}")
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response get(@PathParam("label") String label,
                        @PathParam("property") String property,
                        @PathParam("value") String value) {

        if (label == null || property == null || value == null) {
            return Response.status(Status.NOT_FOUND).build();
        }
        JSONObject jsonObj = new JSONObject();
        try (Transaction tx = db.beginTx()) {
            Node systemNode = db.findNode(Nodes.__SYSTEM__, PROP_LABEL, label);
            if(systemNode == null) {
                return Response.status(Status.NOT_FOUND).build();
            }
            Object propValue = str2obj(value, (String)systemNode.getProperty(property));
            Iterator objNodes = db.findNodes(Label.label(label), property, propValue);
            while (objNodes.hasNext()) {
                Node curNode = (Node)objNodes.next();
                Iterable<String> sysProperties = systemNode.getPropertyKeys();
                for(String curProperty: sysProperties) {
                    if(!isSystemProperty(curProperty)) {
                        try {
                            jsonObj.put(curProperty, curNode.getProperty(curProperty));
                        } catch (org.neo4j.graphdb.NotFoundException e) {}
                    }
                }
            }
            tx.success();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.ok(jsonObj).build();
    }
}
