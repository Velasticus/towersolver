package com.kalulu.sgs.swtor.towersolver

case class InvalidMoveException extends RuntimeException

class TowerOfHanoiState(val pieces : Array[Int]) {
  lazy val hc = ( pieces.reverse.view.zipWithIndex :\ 0 ) { case ((value,exp),acc) => acc + value * Math.pow(3,exp).toInt }
  lazy val maxes = ( pieces.view.zipWithIndex :\ Array(0,0,0) ) { case ((value,idx),b) => {
    if ( b(value-1) < idx+1 )
      b(value-1) = idx+1
    b
  }}
  lazy val mins = ( pieces.view.zipWithIndex :\ Array(0,0,0) ) { case ((value,idx),b) => {
    if ( b(value-1) == 0 || b(value-1) > idx+1 )
      b(value-1) = idx+1
    b
  }}
  def isValidMove(from:Int,to:Int) = {
     mins(from) != 0 && ( mins(to) == 0 || mins(from) < mins(to) ) 
  }

  def move(from:Int,to:Int) = {
    if (!isValidMove(from,to))
      throw new InvalidMoveException
    val newPieces = pieces.clone()
    newPieces(mins(from)-1) = to+1
    new TowerOfHanoiState(newPieces)
  }
  
  def findMove(newState:TowerOfHanoiState) = {
    val comb = pieces.zip(newState.pieces).view.zipWithIndex.filter {
      case ((currTower,newTower),index) => currTower != newTower
    }
    if (comb.length != 1)
      throw InvalidMoveException()
    comb.first
  }
  
  override def equals(obj:Any) = {
    obj.isInstanceOf[TowerOfHanoiState] && obj.asInstanceOf[TowerOfHanoiState].hc == hc
  }
  override def hashCode = {
    hc
  }
  override def toString = "(" + ( pieces :\ "" ) ( _ + "," + _ ) + ")"
}

object TowerOfHanoiState {
  def apply(state:Array[Int]) = {
    new TowerOfHanoiState(state)
  }
  def apply(piece:Int,pieces:Int*) : TowerOfHanoiState = {
    apply((piece :: pieces.toList).toArray)
  }
}

