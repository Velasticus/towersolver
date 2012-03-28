package com.kalulu.sgs.swtor.towersolver.traits
import com.sun.sgs.app.ManagedReference

trait Player {
  val id : Int
  val email : String
  val name : String
  var guild : String
  var servers : Map[String,Character]
  var currentServer : String
  var currentCharacter : Character
  }