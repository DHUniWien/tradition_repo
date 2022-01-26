package net.stemmaweb.printer;

//This software requires dot.exe/dot.app/dot to be installed on the system!

// GraphViz.java - a simple API to call dot from Java programs


/*$Id$*/
/*
 ******************************************************************************
 *                                                                            *
 *              (c) Copyright 2003 Laszlo Szathmary                           *
 *                                                                            *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms of the GNU Lesser General Public License as published by   *
 * the Free Software Foundation; either version 2.1 of the License, or        *
 * (at your option) any later version.                                        *
 *                                                                            *
 * This program is distributed in the hope that it will be useful, but        *
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY *
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public    *
 * License for more details.                                                  *
 *                                                                            *
 * You should have received a copy of the GNU Lesser General Public License   *
 * along with this program; if not, write to the Free Software Foundation,    *
 * Inc., 675 Mass Ave, Cambridge, MA 02139, USA.                              *
 *                                                                            *
 ******************************************************************************
 */

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * <dl>
 * <dt>Purpose: GraphViz Java API
 * <dd>
 *
 * <dt>Description:
 * <dd> With this Java class you can simply call dot
 *      from your Java programs
 * <dt>Example usage:
 * <dd>
 * <pre>
 *    GraphViz gv = new GraphViz();
 *    gv.addln(gv.start_graph());
 *    gv.addln("A -&gt; B;");
 *    gv.addln("A -&gt; C;");
 *    gv.addln(gv.end_graph());
 *    System.out.println(gv.getDotSource());
 *
 *    String type = "gif";
 *    File out = new File("out." + type);   // out.gif in this example
 *    gv.writeGraphToFile( gv.getGraph( gv.getDotSource(), type ), out );
 * </pre>
 * </dd>
 *
 * </dl>
 *
 * @version v0.4, 2011/02/05 (February) -- Patch of Keheliya Gallaba is added. Now you
 * can specify the type of the output file: gif, dot, fig, pdf, ps, svg, png, etc.
 * @version v0.3, 2010/11/29 (November) -- Windows support + ability 
 * to read the graph from a text file
 * @version v0.2, 2010/07/22 (July) -- bug fix
 * @version v0.1, 2003/12/04 (December) -- first release
 * @author  Laszlo Szathmary (<a href="jabba.laci@gmail.com">jabba.laci@gmail.com</a>)
 */
public class GraphViz
{
   /**
    * The source of the graph written in dot language.
    */
    private StringBuilder graph = new StringBuilder();

   /**
    * Constructor: creates a new GraphViz object that will contain
    * a graph.
    */
   public GraphViz() {
   }
   
    private String getDotExecutable() {
       String[] possibilities = {"dot", "dot.exe", "dot.bat"};
       for (String exec : possibilities) {
          String dotPath = resolve(exec);
          if (!dotPath.equals(""))
             return dotPath + File.separator + exec;
       }
       return "";
    }

   /**
    * Returns the graph's source description in dot language.
    * @return Source of the graph in dot language.
    */
   public String getDotSource() {
      return graph.toString();
   }

   /**
    * Adds a string to the graph's source (without newline).
    * @param line - the line to add
    */
   public void add(String line) {
      graph.append(line);
   }

   /**
    * Adds a string to the graph's source (with newline).
    * @param line - the line to add
    */
   public void addln(String line) {
      graph.append(line).append("\n");
   }

   /**
    * Adds a newline to the graph's source.
    */
   public void addln() {
      graph.append('\n');
   }

   /**
    * Returns the graph as an image in binary format.
    * @param dot_source Source of the graph to be drawn.
    * @param type Type of the output image to be produced, e.g.: gif, dot, fig, pdf, ps, svg, png.
    * @return A byte array containing the image of the graph.
    */
   public byte[] getGraph(String dot_source, String type)
   {
      File dot;
      byte[] img_stream;
   
      try {
         dot = writeDotSourceToFile(dot_source);
          if (dot == null) {
              return null;
          }
          img_stream = get_img_stream(dot, type);
          if (!dot.delete()) {
              System.err.println("Warning: " + dot.getAbsolutePath() + " could not be deleted!");
          }
          return img_stream;
      } catch (java.io.IOException ioe) { return null; }
   }

   /**
    * Writes the graph's image in a file.
    * @param img   A byte array containing the image of the graph.
    * @param file  Name of the file to where we want to write.
    * @return Success: 1, Failure: -1
    */
   public int writeGraphToFile(byte[] img, String file)
   {
      File to = new File(file);
      return writeGraphToFile(img, to);
   }

   /**
    * Writes the graph's image in a file.
    * @param img   A byte array containing the image of the graph.
    * @param to    A File object to where we want to write.
    * @return Success: 1, Failure: -1
    */
   public int writeGraphToFile(byte[] img, File to)
   {
      try {
         FileOutputStream fos = new FileOutputStream(to);
         fos.write(img);
         fos.close();
      } catch (java.io.IOException ioe) {
          return -1;
      }
      return 1;
   }

   /**
    * It will call the external dot program, and return the image in
    * binary format.
    * @param dot Source of the graph (in dot language).
    * @param type Type of the output image to be produced, e.g.: gif, dot, fig, pdf, ps, svg, png.
    * @return The image of the graph in .gif format.
    */
   private byte[] get_img_stream(File dot, String type)
   {
      File img;
      byte[] img_stream = null;
      
      String DOT = getDotExecutable();
      
      try {
         img = File.createTempFile("graph_", "."+type);
         Runtime rt = Runtime.getRuntime();
         
         // patch by Mike Chenault
         String[] args = {DOT, "-T"+type, dot.getAbsolutePath(), "-o", img.getAbsolutePath()};
         Process p = rt.exec(args);
         
         p.waitFor();

         FileInputStream in = new FileInputStream(img.getAbsolutePath());
         img_stream = new byte[in.available()];
         in.read(img_stream);
         // Close it if we need to
         // if( in != null ) in.close();  --> always true
         in.close();

         if (!img.delete()) {
             System.err.println("Warning: " + img.getAbsolutePath() + " could not be deleted!");
         }
      } catch (java.io.IOException ioe) {
         System.err.println("Error:    in I/O processing of tempfile\n");
         System.err.println("       or in calling external command: " + ioe.getMessage());
         ioe.printStackTrace();
      } catch (java.lang.InterruptedException ie) {
         System.err.println("Error: the execution of the external program was interrupted");
         ie.printStackTrace();
      }

      return img_stream;
   }

   /**
    * Writes the source of the graph in a file, and returns the written file
    * as a File object.
    * @param str Source of the graph (in dot language).
    * @return The file (as a File object) that contains the source of the graph.
    */
   private File writeDotSourceToFile(String str) throws java.io.IOException
   {
      File temp;
      
      try {
         temp = File.createTempFile("graph_", ".dot.tmp");
         FileWriter fout = new FileWriter(temp);
         fout.write(str);
         fout.close();
      }
      catch (Exception e) {
         System.err.println("Error: I/O error while writing the dot source to temp file!");
         return null;
      }
      return temp;
   }

   /**
    * Returns a string that is used to start a graph.
    * @return A string to open a graph.
    */
   public String start_graph() {
      return "digraph G {";
   }

   /**
    * Returns a string that is used to end a graph.
    * @return A string to close a graph.
    */
   public String end_graph() {
      return "}";
   }

   /**
    * Read a DOT graph from a text file.
    * 
    * @param input Input text file containing the DOT graph
    * source.
    */
   public void readSource(String input)
   {
       StringBuilder sb = new StringBuilder();

       try {
           FileInputStream fis = new FileInputStream(input);
           DataInputStream dis = new DataInputStream(fis);
           BufferedReader br = new BufferedReader(new InputStreamReader(dis));
           String line;
           while ((line = br.readLine()) != null) {
               sb.append(line);
           }
           dis.close();
       }
       catch (Exception e) {
           System.err.println("Error: " + e.getMessage());
       }

       this.graph = sb;
   }
   
   private static String resolve(String executable)
   {
      Optional<Path> execInPath = Stream.of(System.getenv("PATH").split(Pattern.quote(File.pathSeparator)))
              .map(Paths::get)
              .filter(path -> Files.exists(path.resolve(executable))).findFirst();
      if (execInPath.isPresent())
         return execInPath.get().toString();
      else
         return "";
   }
   
} // end of class GraphViz

