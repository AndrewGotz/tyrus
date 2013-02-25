/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2013 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.tyrus.test.e2e;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.websocket.ClientEndpointConfiguration;
import javax.websocket.ClientEndpointConfigurationBuilder;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.server.Server;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the basic client behavior, sending and receiving message
 *
 * @author Danny Coward (danny.coward at oracle.com)
 * @author Martin Matula (martin.matula at oracle.com)
 */
public class StreamingTextAnnotTest {

    @Test
    public void testClient() {
        final ClientEndpointConfiguration cec = ClientEndpointConfigurationBuilder.create().build();
        Server server = new Server(StreamingTextAnnotEndpoint.class);

        try {
            server.start();
            CountDownLatch messageLatch = new CountDownLatch(2);
            StreamingTextAnnotEndpoint.messageLatch = messageLatch;

            StreamingTextTest.StreamingTextClient stc = new StreamingTextTest.StreamingTextClient(messageLatch);
            ClientManager client = ClientManager.createClient();
            client.connectToServer(stc, cec, new URI("ws://localhost:8025/websockets/tests/streamingtext"));

            messageLatch.await(5, TimeUnit.SECONDS);
            Assert.assertTrue("The client did not get anything back", stc.gotSomethingBack);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            server.stop();
        }
    }

    /**
     * @author Martin Matula (martin.matula at oracle.com)
     */
    @ServerEndpoint(value = "/streamingtext")
    public static class StreamingTextAnnotEndpoint {
        private Session session;
        private StringBuilder sb = new StringBuilder();
        static CountDownLatch messageLatch;

        @OnOpen
        public void onOpen(Session session) {
            System.out.println("STREAMINGSERVER opened !");
            this.session = session;
            try {
                System.out.println(session.getBasicRemote());
                sendPartial("thank ", false);
                sendPartial("you ", false);
                sendPartial("very ", false);
                sendPartial("much ", false);
                sendPartial("!", true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @OnMessage
        public void onMessage(String text, boolean last) {
            System.out.println("STREAMINGSERVER piece came: " + text);
            sb.append(text);
            if (last) {
                System.out.println("STREAMINGSERVER whole message: " + sb.toString());
                sb = new StringBuilder();
                messageLatch.countDown();
            } else {
                System.out.println("Resuming the client...");
                synchronized (StreamingTextTest.StreamingTextClient.class) {
                    StreamingTextTest.StreamingTextClient.class.notify();
                }
            }
        }

        private void sendPartial(String partialString, boolean isLast) throws IOException, InterruptedException {
            System.out.println("Server sending: " + partialString);
            session.getBasicRemote().sendText(partialString, isLast);
        }
    }
}
