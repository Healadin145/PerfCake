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
package org.perfcake.reporting;

import org.perfcake.RunInfo;
import org.perfcake.common.BoundPeriod;
import org.perfcake.common.PeriodType;
import org.perfcake.reporting.destinations.Destination;
import org.perfcake.reporting.reporters.Reporter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Controls the reporting facilities.
 *
 * @author <a href="mailto:marvenec@gmail.com">Martin Večeřa</a>
 */
public class ReportManager {

   private static final Logger log = LogManager.getLogger(ReportManager.class);

   private volatile boolean resetLastTimes = false;

   /**
    * Set of reporters registered for reporting.
    */
   private final Set<Reporter> reporters = new CopyOnWriteArraySet<>();

   /**
    * Current run info to control the measurement.
    */
   private RunInfo runInfo;

   /**
    * Thread to assure time based periodical reporting.
    */
   private Thread periodicThread;

   /**
    * Creates a new {@link org.perfcake.reporting.MeasurementUnit measurement unit} with a unique iteration number.
    *
    * @return A {@link org.perfcake.reporting.MeasurementUnit measurement unit} unit with a unique iteration number, or null if a measurement is not running or is already finished.
    */
   public MeasurementUnit newMeasurementUnit() {
      if (!runInfo.isRunning()) {
         return null;
      }

      if (log.isTraceEnabled()) {
         log.trace("Creating a new measurement unit.");
      }

      return new MeasurementUnit(runInfo.getNextIteration());
   }

   /**
    * Sets {@link org.perfcake.RunInfo} for the current measurement run.
    *
    * @param runInfo
    *       The RunInfo that contains information about the current measurement.
    */
   public void setRunInfo(final RunInfo runInfo) {
      if (log.isDebugEnabled()) {
         log.debug("A new run info set " + runInfo);
      }

      this.runInfo = runInfo;
      for (final Reporter r : reporters) {
         r.setRunInfo(runInfo);
      }
   }

   /**
    * Reports a newly measured {@link MeasurementUnit}. Each Measurement Unit must be reported exactly once.
    *
    * @param measurementUnit
    *       A MeasurementUnit to be reported.
    * @throws ReportingException
    *       If reporting could not be done properly.
    */
   public void report(final MeasurementUnit measurementUnit) throws ReportingException {
      if (log.isTraceEnabled()) {
         log.trace("Reporting a new measurement unit " + measurementUnit);
      }

      ReportingException e = null;

      if (runInfo.isStarted()) { // cannot use isRunning while we still want the last iteration to be reported
         for (final Reporter r : getReporters()) {
            try {
               r.report(measurementUnit);
            } catch (final ReportingException re) {
               log.warn("Error reporting a measurement unit " + measurementUnit, re);
               e = re; // store the latest exception and give chance to other reporters as well
            }
         }
      } else {
         if (log.isDebugEnabled()) {
            log.debug("Skipping the measurement unit (" + measurementUnit + ") because the ReportManager is not started.");
         }
      }

      if (e != null) {
         throw e;
      }
   }

   /**
    * Resets reporting to the zero state. It is used after the warm-up period.
    */
   public void reset() {
      if (log.isDebugEnabled()) {
         log.debug("Resetting reporting.");
      }

      runInfo.reset();
      resetLastTimes = true;
      for (final Reporter r : reporters) {
         r.reset();
      }
   }

   /**
    * Registers a new {@link org.perfcake.reporting.reporters.Reporter}.
    *
    * @param reporter
    *       A reporter to be registered.
    */
   public void registerReporter(final Reporter reporter) {
      if (log.isDebugEnabled()) {
         log.debug("Registering a new reporter " + reporter);
      }

      reporter.setReportManager(this);
      reporter.setRunInfo(runInfo);
      reporters.add(reporter);
   }

   /**
    * Removes a registered {@link org.perfcake.reporting.reporters.Reporter}.
    *
    * @param reporter
    *       A reporter to unregistered.
    */
   public void unregisterReporter(final Reporter reporter) {
      if (log.isDebugEnabled()) {
         log.debug("Removing the reporter " + reporter);
      }

      reporter.setReportManager(null);
      reporter.setRunInfo(null);
      reporters.remove(reporter);
   }

   /**
    * Gets an immutable set of current reporters.
    *
    * @return An immutable set of currently registered reporters.
    */
   public Set<Reporter> getReporters() {
      return Collections.unmodifiableSet(reporters);
   }

   /**
    * Starts the reporting facility.
    */
   public void start() {
      if (log.isDebugEnabled()) {
         log.debug("Starting reporting and all reporters.");
      }

      runInfo.start(); // runInfo must be started first, otherwise the time monitoring thread in AbstractReporter dies immediately

      reporters.forEach(org.perfcake.reporting.reporters.Reporter::start);

      periodicThread = new Thread(new Runnable() {
         public void run() {
            long now;
            Long lastTime;
            Destination d;
            Map<Reporter, Map<Destination, Long>> reportLastTimes = new HashMap<>();
            Map<Destination, Long> lastTimes;

            try {
               while ((runInfo.isRunning() && !periodicThread.isInterrupted())) {

                  if (resetLastTimes) {
                     reportLastTimes = new HashMap<>();
                     resetLastTimes = false;
                  }

                  now = System.currentTimeMillis();

                  for (final Reporter r : reporters) {
                     lastTimes = reportLastTimes.get(r);

                     if (lastTimes == null) {
                        lastTimes = new HashMap<>();
                        reportLastTimes.put(r, lastTimes);
                     }

                     for (final BoundPeriod<Destination> p : r.getReportingPeriods()) {
                        d = p.getBinding();
                        lastTime = lastTimes.get(d);

                        if (lastTime == null) {
                           lastTime = now;
                           lastTimes.put(d, lastTime);
                        }

                        if (p.getPeriodType() == PeriodType.TIME && lastTime + p.getPeriod() < now && runInfo.getIteration() >= 0) {
                           lastTimes.put(d, now);
                           try {
                              r.publishResult(PeriodType.TIME, d);
                           } catch (final ReportingException e) {
                              log.warn("Unable to publish result: ", e);
                           }
                        }
                     }
                  }

                  Thread.sleep(500);
               }
            } catch (final InterruptedException e) {
               // this means our job is done
            }
            if (log.isDebugEnabled()) {
               log.debug("Gratefully terminating the periodic reporting thread.");
            }
         }
      });
      periodicThread.setDaemon(true); // allow the thread to die with JVM termination and do not block it
      periodicThread.start();
   }

   /**
    * Makes sure 100% is reported for time based destinations after the end of the test.
    */
   private void reportFinalTimeResults() {
      log.info("Reporting final results:");
      for (final Reporter r : reporters) {
         for (final BoundPeriod<Destination> bp : r.getReportingPeriods()) {
            try {
               if (bp.getPeriodType() == PeriodType.TIME) {
                  r.publishResult(bp.getPeriodType(), bp.getBinding());
               }
            } catch (final ReportingException e) {
               log.error(String.format("Could not report final result for reporter %s and destination %s.", r.toString(), bp.getBinding().toString()), e);
            }
         }
      }
      if (log.isDebugEnabled()) {
         log.debug("End of final results.");
      }
   }

   /**
    * Stops the reporting facility.
    */
   public void stop() {
      if (log.isDebugEnabled()) {
         log.debug("Stopping reporting and all reporters.");
      }

      runInfo.stop();

      reportFinalTimeResults();

      for (final Reporter r : reporters) {
         r.stop();
      }

      if (periodicThread != null) {
         periodicThread.interrupt();
      }
      periodicThread = null;
   }
}
