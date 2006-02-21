/*
  * JBoss, Home of Professional Open Source
  * Copyright 2005, JBoss Inc., and individual contributors as indicated
  * by the @authors tag. See the copyright.txt in the distribution for a
  * full listing of individual contributors.
  *
  * This is free software; you can redistribute it and/or modify it
  * under the terms of the GNU Lesser General Public License as
  * published by the Free Software Foundation; either version 2.1 of
  * the License, or (at your option) any later version.
  *
  * This software is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
  * Lesser General Public License for more details.
  *
  * You should have received a copy of the GNU Lesser General Public
  * License along with this software; if not, write to the Free
  * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
  * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
  */
package org.jboss.test.messaging.jms;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.management.ObjectName;
import javax.naming.InitialContext;

import org.jboss.jms.server.ConnectionManager;
import org.jboss.logging.Logger;
import org.jboss.test.messaging.MessagingTestCase;
import org.jboss.test.messaging.tools.ServerManagement;
import org.jboss.test.messaging.tools.jmx.rmi.LocalTestServer;
import org.jboss.test.messaging.tools.jmx.rmi.Server;
import org.jboss.test.messaging.tools.jndi.InVMInitialContextFactory;

/**
 * 
 * A CallbackFailureTest.
 * 
 * @author <a href="tim.fox@jboss.com">Tim Fox</a>
 * @version 1.1
 *
 * CallbackFailureTest.java,v 1.1 2006/02/21 08:22:28 timfox Exp
 */
public class CallbackFailureTest extends MessagingTestCase
{
   // Constants -----------------------------------------------------

   // Static --------------------------------------------------------
   
   private static final Logger log = Logger.getLogger(CallbackFailureTest.class);
   
   // Attributes ----------------------------------------------------
   
   protected Server localServer;
   
   protected Server remoteServer;

   // Constructors --------------------------------------------------

   public CallbackFailureTest(String name)
   {
      super(name);
   }

   // Public --------------------------------------------------------

   public void setUp() throws Exception
   {
      super.setUp();
      
      //Start the local server
      localServer = new LocalTestServer();
      
      //Start all the services locally
      localServer.start("all");
      
      log.info("Started local server");
            
      localServer.deployQueue("Queue", null);
      
      log.info("deployed queue");
      
      //Connect to the remote server, but don't start a servicecontainer on it
      //We are only using the remote server to open a client connection to the local server
      ServerManagement.create();
      
      log.info("Connected to server");
      
      remoteServer = ServerManagement.getServer();
   }

   public void tearDown() throws Exception
   {       
      localServer.stop();
   }
        
   /*
    * Test that when a client callback fails, server side resources for connections are cleaned-up
    */
   public void testCallbackFailure() throws Exception
   {
      if (!ServerManagement.isRemote()) return;
      
      //We need to disable exception listener otherwise it will clear up the connection itself
      
      ObjectName connectorName = localServer.getServerPeer().getConnector();
      
      ConnectionManager cm = localServer.getServerPeer().getConnectionManager();
      
      localServer.getServerPeer().getServer().invoke(connectorName, "removeConnectionListener",
                                                     new Object[] {cm},
                                                     new String[] {"org.jboss.remoting.ConnectionListener"}); 
       
      InitialContext ic = new InitialContext(InVMInitialContextFactory.getJNDIEnvironment());
      
      ConnectionFactory cf = (ConnectionFactory)ic.lookup("/ConnectionFactory");
      
      Queue queue = (Queue)ic.lookup("/queue/Queue");
      
      CreateHangingConsumerCommand command = new CreateHangingConsumerCommand(cf, queue);
      
      String remotingSessionId = (String)remoteServer.executeCommand(command);
      
      remoteServer.exit();
      
      //we have removed the exception listener so the server side resouces shouldn't be cleared up
      
      Thread.sleep(20000);
                 
      assertNotNull(cm.getConnection(remotingSessionId));
      
      //Now we send a message which should prompt delivery to the dead consumer causing
      //an exception which should cause connection cleanup
                  
      Connection conn = cf.createConnection();
      
      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        
      MessageProducer prod = sess.createProducer(queue);
      
      prod.send(sess.createMessage());
      prod.send(sess.createMessage());
      prod.send(sess.createMessage());
      prod.send(sess.createMessage());
      prod.send(sess.createMessage());
      prod.send(sess.createMessage());
      prod.send(sess.createMessage());
      prod.send(sess.createMessage());
      prod.send(sess.createMessage());
      prod.send(sess.createMessage());
      prod.send(sess.createMessage());
      
      log.info("Sent messages **************");
       
      Thread.sleep(10000);
      
      assertNull(cm.getConnection(remotingSessionId));   
               
   }
   
   // Package protected ---------------------------------------------
   
   // Protected -----------------------------------------------------
   
   // Private -------------------------------------------------------
   
  
   // Inner classes -------------------------------------------------

}
