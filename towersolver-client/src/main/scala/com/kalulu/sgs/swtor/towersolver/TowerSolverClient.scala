package com.kalulu.sgs.swtor.towersolver

import java.awt.Dimension
import java.io.UnsupportedEncodingException
import java.net.PasswordAuthentication
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import java.util.Properties
import scala.collection.mutable.Map
import scala.swing.BorderPanel.Position.Center
import scala.swing.BorderPanel.Position.South
import scala.swing.BorderPanel.Position.West
import scala.swing.event.EditDone
import scala.swing.BorderPanel
import scala.swing.ComboBox
import scala.swing.Label
import scala.swing.MainFrame
import scala.swing.Panel
import scala.swing.ScrollPane
import scala.swing.SimpleSwingApplication
import scala.swing.TextArea
import scala.swing.TextField
import scala.util.Random
import com.sun.sgs.client.simple.SimpleClient
import com.sun.sgs.client.simple.SimpleClientListener
import com.sun.sgs.client.ClientChannel
import com.sun.sgs.client.ClientChannelListener
import com.weiglewilczek.slf4s.Logging
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.ComboBoxModel
import scala.swing.event.SelectionChanged

object TowerSolverClient extends SimpleSwingApplication with SimpleClientListener with Logging {
  
  final val HOST_PROPERTY = "tutorial.host"
  final val DEFAULT_HOST = "localhost"
  final val PORT_PROPERTY = "tutorial.port"
  final val DEFAULT_PORT = "1139"
  final val MESSAGE_CHARSET = "UTF-8"

  final val channelsByName = Map[String,ClientChannel]()
  final val channelNumber = new AtomicInteger(1)
  
  val simpleClient = new SimpleClient(this)
  val random = new Random

  lazy val statusLabel : Label = new Label {
    focusable = false
  }
  
  lazy val outputArea = new TextArea {
    editable = false
    focusable = false
  }
    
  def setStatus(status:String) {
    statusLabel.text = "Status: " + status
    appendOutput(status)
  }
  
  def appendOutput(x:String) {
    outputArea.append(x+"\n")
  }
  lazy val inputField = new TextField 
  lazy val inputPanel = new BorderPanel {
    enabled = false
    layout(inputField) = Center
  }
  
  lazy val comboBox = new ComboBox[String](List()) {
    focusable = false
    lazy val comboBoxModel = new DefaultComboBoxModel[String]()
    lazy val peer1 : JComboBox[String] = peer.asInstanceOf[JComboBox[String]]
    peer1.setModel(comboBoxModel)
    
    comboBoxModel.addElement("<DIRECT>")
  }
  
  lazy val appPanel = new BorderPanel {
    
    layout( new ScrollPane( outputArea )) = Center
    
    listenTo(inputField)
    
    reactions += {
      case EditDone(_) => {
        if (simpleClient.isConnected) {
          val txt = getInputText
          if (txt.length > 0)
            channelsByName.get(comboBox.selection.item) match {
              case Some(channel) => channel.send(encode(txt))
              case None => sendMsg(txt)
            }
        }
      }
    }

    layout(inputPanel) = South    
    layout(comboBox) = West
      
  }
  
  lazy val ui : Panel = new BorderPanel {
      var reactLive = false

      layout(appPanel) = Center
      layout(statusLabel) = South
      setStatus("Not Started")
      
  }
  lazy val top = new MainFrame {
    title = "Tower Solver"
    contents = ui
    size = new Dimension(640,480)
  }
  
  def login = {
    val host = System.getProperty(HOST_PROPERTY,DEFAULT_HOST)
    val port = System.getProperty(PORT_PROPERTY,DEFAULT_PORT)
    
    try {
      val props = new Properties
      props.put("host", host)
      props.put("port", port)
      simpleClient.login(props)
    } catch {
      case e => logger.error("Error connecting to server",e)
      disconnected(false,e.getMessage)
    }
  }
  
  def encode(s:String) = {
    try {
      ByteBuffer.wrap(s.getBytes(MESSAGE_CHARSET))
    } catch {
      case e: UnsupportedEncodingException => throw new Error("Required character set " + MESSAGE_CHARSET + " not found", e)
    }
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
  
  def getInputText = {
    try {
      inputField.text
    } finally {
      inputField.text = ""
    }
  }
  
  // Implement SimpleClientListener
  override def getPasswordAuthentication = {
    val player = "guest-" + random.nextInt(1000)
    setStatus("Logging in as " + player)
    val password = "guest"
    new PasswordAuthentication(player, password.toCharArray)
  }
  
  override def loggedIn = {
    inputPanel.enabled = true
    setStatus("Logged in")
  }
  
  override def loginFailed(reason:String) = {
    setStatus("Login failed: " + reason)
  }
  
  override def disconnected(graceful:Boolean, reason: String) = {
    inputPanel.enabled = false
    setStatus("Disconnected: " + reason)
  }
  
  override def joinedChannel(channel:ClientChannel) = {
    val name = channel.getName
    channelsByName.put(name, channel)
    appendOutput("Joined to channel " + name)
    comboBox.comboBoxModel.addElement(name)
    
    new TowerSolverChannelListener()
  }
    
  override def receivedMessage(message:ByteBuffer) {
    appendOutput("Server sent: " + decode(message))
  }
  
  override def reconnected = {
    setStatus("reconnected")
  }
  
  override def reconnecting = {
    setStatus("reconnecting")
  }

  override def startup(args: Array[String]) = {
    super.startup(args)
    
    login
  }

  def handleInput = {
  }
  
  def sendMsg(text:String) = {
    val msg = encode(text)
    simpleClient.send(msg)
  }
  
  class TowerSolverChannelListener extends ClientChannelListener {
    private final val channelNumber = TowerSolverClient.channelNumber.getAndIncrement
    
    override def leftChannel(channel:ClientChannel) {
      appendOutput("Removed from channel " + channel.getName)
    }
    
    override def receivedMessage(channel:ClientChannel, message:ByteBuffer) {
      appendOutput("[" + channel.getName + "/" + channelNumber + "] " + decode(message))
    }
  }
}

