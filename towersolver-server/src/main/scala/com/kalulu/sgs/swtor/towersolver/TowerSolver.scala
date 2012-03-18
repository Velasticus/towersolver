package com.kalulu.sgs.swtor.towersolver

import java.util.Properties
import com.sun.sgs.app.AppContext
import com.sun.sgs.app.AppListener
import com.sun.sgs.app.ClientSession
import com.sun.sgs.app.ClientSessionListener
import com.sun.sgs.app.Task
import com.weiglewilczek.slf4s.Logging
import com.sun.sgs.app.ManagedObject
import com.sun.sgs.app.ManagedReference
import java.nio.ByteBuffer
import java.io.UnsupportedEncodingException
import com.sun.sgs.app.Channel
import com.sun.sgs.app.Delivery
import com.sun.sgs.app.ChannelListener

@serializable
class TowerSolver extends AppListener with Task with Logging {
  private var loginCount = 0
  private var channel1 : ManagedReference[Channel] = null

  private var subTaskRef : ManagedReference[TowerTimedTask] = null
  import TowerSolver._
  
  override def initialize(props:Properties) {
    setSubTask(new TowerTimedTask)
     
    val taskManager = AppContext.getTaskManager
    val chanManager = AppContext.getChannelManager
    
    taskManager.schedulePeriodicTask(new TowerTimedTask, DELAY_MS, PERIOD_MS)
    
    val c1 = chanManager.createChannel(CHANNEL_1_NAME, null, Delivery.RELIABLE)
    channel1 = AppContext.getDataManager.createReference(c1)
    chanManager.createChannel(CHANNEL_2_NAME, new TowerSolverChannelListener, Delivery.RELIABLE)
    
  }
    
  override def loggedIn(session:ClientSession) : ClientSessionListener = {
    loginCount+=1
    logger.info("User " + session.getName + " has almost logged in")
    new TowerUserSessionListener(session,channel1,loginCount)
  }

  override def run : Unit = {
    getSubTask match {
      case null => logger.error("Unable to find subtask")
      case subTask => subTask.run
    }
  }
    
  def getSubTask : TowerTimedTask = {
    if (subTaskRef != null)
      subTaskRef.get
    null
  }
    
  def setSubTask(subTask:TowerTimedTask) = {
    if (subTask==null)
      subTaskRef = null
    else {
      val dataManager = AppContext.getDataManager
      subTaskRef = dataManager.createReference(subTask)
    }
  }
}

@serializable
class TowerTimedTask extends ManagedObject with Task with Logging {
  private var lastTimestamp = System.currentTimeMillis
    
  override def run : Unit = {
    val timestamp = System.currentTimeMillis
    val delta = timestamp - lastTimestamp
    lastTimestamp = timestamp

    logger.info("TowerTimer task: running at timestamp " + timestamp + " elapsed " + delta);
  }
}

object TowerSolver {
  final val CHANNEL_1_NAME = "Foo"
  final val CHANNEL_2_NAME = "Bar"
    
  private final val DELAY_MS  = 5000
  private final val PERIOD_MS = 5000
}

@serializable
class TowerUserSessionListener(session:ManagedReference[ClientSession],channel:ManagedReference[Channel],private val name:String,private val loginCount:Int) extends ClientSessionListener with Logging {
  def this(session:ClientSession,channel:ManagedReference[Channel],loginCount:Int) = this(AppContext.getDataManager().createReference(session),channel,session.getName,loginCount)
  final val MESSAGE_CHARSET = "UTF-8"
    
  channel.get.join(session.get)
  
  AppContext.getChannelManager.getChannel(TowerSolver.CHANNEL_2_NAME).join(session.get)
  
  override def receivedMessage(message:ByteBuffer) = {
    val s = decode(message)
    logger.info(s)
    session.get.send(encode(s))
  }
  
  override def disconnected(graceful:Boolean) = {
    val g = if(graceful) "gracefully" else "forced"
    logger.info("User " + name + " has logged out " + g)
  }
  def decode(b:ByteBuffer) = {
    try {
      val bytes = new Array[Byte](b.remaining)
      b.get(bytes)
      new String(bytes, MESSAGE_CHARSET)
    } catch {
      case e: UnsupportedEncodingException => throw new Error("Required character set " + MESSAGE_CHARSET + " not found", e)
    }
  }
  def encode(s:String) = {
    try {
      ByteBuffer.wrap(s.getBytes())
    } catch {
      case e: UnsupportedEncodingException => throw new Error("Required character set " + MESSAGE_CHARSET + " not found", e)
    }
  }
  
}

@serializable
class TowerSolverChannelListener extends ChannelListener with Logging {
  override def receivedMessage(channel:Channel,session:ClientSession,message:ByteBuffer) = {
   logger.info("Channel message from " + session.getName + " on " + channel.getName)
   channel.send(session,message)
  }
}
