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
package org.perfcake.message.generator;

import org.perfcake.PerfCakeConst;
import org.perfcake.util.Utils;

import java.util.concurrent.Semaphore;

/**
 * A bidirectional communication channel between {@link MessageGenerator} and {@link SenderTask}.
 *
 * @author <a href="mailto:marvenec@gmail.com">Martin Večeřa</a>
 */
public class CanalStreet {

   /**
    * A {@link MessageGenerator} that will be notified about possible errors.
    */
   private MessageGenerator generator;

   /**
    * A {@link Semaphore} that will be released upon successful sending of a message. Can be null.
    */
   private Semaphore semaphore;

   /**
    * Should we interrupt the generator if there is an error?
    */
   private final boolean failFast;

   /**
    * Establishes a new communication channel that can be passed to a {@link SenderTask}.
    *
    * @param generator
    *       A {@link MessageGenerator} that will be notified about possible errors.
    * @param semaphore
    *       A {@link Semaphore} that will be released upon successful sending of a message. Can be null.
    * @param failFast
    *       Do we want to interrupt the generator if there is an error?
    */
   CanalStreet(final MessageGenerator generator, final Semaphore semaphore, final boolean failFast) {
      this.generator = generator;
      this.semaphore = semaphore;
      this.failFast = failFast;
   }

   /**
    * Acknowledges completion of the sender task.
    * The semaphore is released when the task is finished. This is used to control the maximum number sender tasks created
    * and waiting for execution.
    */
   void acknowledgeSend() {
      if (semaphore != null) {
         semaphore.release();
      }
   }

   /**
    * Reports a sender error to the generator in case we are supposed to fail fast.
    *
    * @param e
    *       The root cause of the interruption.
    */
   void senderError(final Exception e) {
      if (failFast) {
         generator.interrupt(e);
      }
   }
}
