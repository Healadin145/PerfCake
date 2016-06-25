/*
 * -----------------------------------------------------------------------\
 * PerfCake
 *  
 * Copyright (C) 2010 - 2016 the original author or authors.
 *  
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -----------------------------------------------------------------------/
 */
package org.perfcake.message.correlator;

import org.perfcake.message.Message;
import org.perfcake.message.generator.SenderTask;

import java.io.Serializable;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import io.vertx.core.MultiMap;

/**
 * Default implementation of a correlator that provides an easy way to extend it by providing
 * the minimum functionality that is necessary, which is correlation id extraction from request and response.
 * The response must never be received prior to sending a request. This might look like an obvious statement
 * but there are cases when this optimization can play a role.
 *
 * @author <a href="mailto:marvenec@gmail.com">Martin Večeřa</a>
 */
public abstract class AbstractCorrelator implements Correlator {

   /**
    * Active tasks waiting for the response. We must never receive a response before the request.
    */
   private Map<String, SenderTask> waitingTasks = new ConcurrentHashMap<>();

   @Override
   public void registerRequest(final SenderTask senderTask, final Message message, final Properties messageAttributes) {
      waitingTasks.put(getRequestCorrelationId(message, messageAttributes), senderTask);
   }

   /**
    * Extracts the correlation id from the request.
    *
    * @param message
    *       The request message.
    * @param messageAttributes
    *       The request message attributes.
    * @return The correlation id corresponding to this request.
    */
   abstract public String getRequestCorrelationId(final Message message, final Properties messageAttributes);

   @Override
   public void registerResponse(final Serializable response, final MultiMap headers) {
      waitingTasks.remove(getResponseCorrelationId(response, headers)).registerResponse(response);
   }

   /**
    * Extracts the correlation id from the response.
    *
    * @param response
    *       The received response.
    * @return The correlation id corresponding to this response.
    */
   abstract public String getResponseCorrelationId(final Serializable response, final MultiMap headers);
}
