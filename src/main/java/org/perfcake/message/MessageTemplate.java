/*
 * -----------------------------------------------------------------------\
 * PerfCake
 *  
 * Copyright (C) 2010 - 2013 the original author or authors.
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
package org.perfcake.message;

import java.io.Serializable;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.perfcake.util.Utils;
import org.perfcake.util.properties.DefaultPropertyGetter;
import org.perfcake.validation.MessageValidator;

/**
 * TODO logging and javadoc
 * @author Lucie Fabriková <lucie.fabrikova@gmail.com>
 * @author Martin Večeřa <marvenec@gmail.com>
 */
public class MessageTemplate implements Serializable {
   private static final long serialVersionUID = 6172258079690233417L;

   private static final String propertyPattern = "[^\\\\](#\\{([^#\\{:]+)(:[^#\\{:]*)?})";

   private transient Logger log = Logger.getLogger(MessageTemplate.class);

   private final Message message;
   private final long multiplicity;
   private final List<String> validatorIds;
   private transient Pattern pattern;

   public Matcher getMatcher(String text) {
      return pattern != null ? pattern.matcher(text) : null;
   }

   public MessageTemplate(final Message message, final long multiplicity, final List<String> validatorIds) {
      this.message = message;
      preparePattern();
      this.multiplicity = multiplicity;
      this.validatorIds = validatorIds;
   }

   public Message getMessage() {
      return message;
   }

   public Message getFilteredMessage(final Properties props) {
      if (pattern != null) {
         final Message m = MessageFactory.getMessage();
         String text = this.getMessage().getPayload().toString();
         text = Utils.filterProperties(text, getMatcher(text), new DefaultPropertyGetter(props));

         m.setPayload(text);
         m.setHeaders(message.getHeaders());
         m.setProperties(message.getProperties());

         return m;
      } else {
         return message;
      }
   }

   private void preparePattern() {
      this.pattern = null;

      // find out if there are any attributes in the text message to be replaced
      if (message.getPayload() instanceof String) {
         final String filteredString = (String) message.getPayload();
         final Pattern pattern = Pattern.compile(propertyPattern);
         final Matcher matcher = pattern.matcher(filteredString);
         if (matcher.find()) {
            if (log.isDebugEnabled()) {
               log.debug("Created matching pattern for the message payload with properties.");
            }
            this.pattern = pattern;
         }
      }

   }

   public Long getMultiplicity() {
      return multiplicity;
   }

   public List<String> getValidatorIds() {
      return validatorIds;
   }
}
