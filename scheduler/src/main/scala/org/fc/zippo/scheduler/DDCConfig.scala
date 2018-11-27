package org.fc.zippo.scheduler

import onight.tfw.outils.conf.PropHelper

object DDCConfig {
  val prop = new PropHelper(null);
  val PREFIX = "org.zippo.ddc."

  val DAEMON_WORKER_THREAD_COUNT = prop.get(PREFIX + "daemon.actor.thread.count", Runtime.getRuntime().availableProcessors());
  val DEFAULT_WORKER_QUEUE_MAXSIZE = prop.get(PREFIX + "default.actor.queue.maxsize", 10);
  val DEFAULT_WORKER_THREAD_COUNT = prop.get(PREFIX + "default.actor.thread.count", Runtime.getRuntime().availableProcessors() * 4);
  val DEFAULT_DISPATCHER_QUEUE_WAIT_MS = prop.get(PREFIX + "default.dispatcher.queue.wait.ms", 60000);
  val DEFAULT_DISPATCHER_COUNT = prop.get(PREFIX + "default.dispatcher.count", 1 + Runtime.getRuntime().availableProcessors() / 2);
  
  val WORKER_OBJECT_POOL_SIZE = prop.get(PREFIX + "runner.object.pool.size", 1024);

  val SPEC_ACTORS = prop.get(PREFIX + "spec.actors", "JINDOB[]");

  
}