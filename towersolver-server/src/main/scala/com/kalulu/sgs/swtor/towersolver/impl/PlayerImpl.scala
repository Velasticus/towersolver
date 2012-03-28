package com.kalulu.sgs.swtor.towersolver.impl

import scala.annotation.serializable

import com.kalulu.sgs.swtor.towersolver.traits.Character
import com.kalulu.sgs.swtor.towersolver.traits.Player
import com.sun.sgs.app.Channel
import com.sun.sgs.app.ManagedObject
import com.sun.sgs.app.ManagedReference

@serializable
class PlayerImpl( val id : Int, val email : String, val name : String ) extends Player with ManagedObject {
  
  var guild : String = null
  var servers : Map[String,Character] = null
  var currentServer : String = null
  var currentCharacter : Character = null
  var channelRef : ManagedReference[Channel] = null
//  var gameRef : ManagedReference[TowerGame] = null

}