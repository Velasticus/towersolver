package com.kalulu.sgs.swtor.towersolver.service

import com.sun.sgs.service.TransactionProxy
import com.sun.sgs.kernel.ComponentRegistry
import java.util.Properties
import com.sun.sgs.services.impl.service.AsyncTaskService
import com.sun.sgs.services.app.AsyncRunnable
import com.sun.sgs.services.app.AsyncCallable

abstract class PersistenceServiceImpl(props:Properties,systemRegistry:ComponentRegistry,txnProxy:TransactionProxy) extends PersistenceService {
  def this() = this(null,null,null)
  val taskService = txnProxy.getService(classOf[AsyncTaskService])
  
  def ready = {
    println("PersistenceServiceImpl started")
  }
  
  def shutdown = {
    isShutdown = true
  }
  
  def getName = this.getClass.getName
  
  private var isShutdown = false
}
