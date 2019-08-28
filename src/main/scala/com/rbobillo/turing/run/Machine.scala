package com.rbobillo.turing.run

import cats.effect.{ExitCode, IO}

import com.rbobillo.turing.description.{Character, Description, MachineState, Step, Transition}
import com.rbobillo.turing.io.Output
import com.rbobillo.turing.tape.Cursor
import com.rbobillo.turing.tape.CursorImplicits.CharZipper

case class Machine(ds: Description, input: String, sz: Int, pretty: Boolean = false) {

  private def currentTransition(read: String, ms: MachineState): String = {
    val mx = ds.machineStates.states.map(_.value.length).max
    val ts = ds.transitions.transitions.filter(_.state == ms)
    val ss = ts.flatMap(_.steps.filter(_.read.value == read))
    s"""($ms, ${"".padTo(mx - ms.value.length, " ").mkString}${Character(read)}) -> ${ss.headOption.getOrElse("?")}"""
  }

  private def nextStep(cursor: Cursor[String], ms: MachineState): String =
    s"${cursor stringify sz} ${currentTransition(cursor.current, ms)}"

  private def findNextStep(ms: MachineState, read: Character): Option[Step] =
    ds.transitions.transitions
      .filter(_.state == ms)
      .flatMap(_.steps.filter(_.read == read))
      .headOption
      .orElse { // This error should be handled with description file parsing
        println(s"\u001b[91mState error\u001b[0m: Cannot find next Step for : ${s"($ms, $read)"}")
        None
      }

  private def compute(cursor: Cursor[String], ms: MachineState): IO[ExitCode] =
    cursor match {
      case Cursor(_, _, _) if ds.finals.states.contains(ms) => IO.pure(ExitCode.Success)
      case Cursor(_, c, _) => for {
        ns <- IO.pure(nextStep(cursor, ms))
        _  <- Output.printSteps(ns, ds, pretty)
        cp <- findNextStep(ms, Character(c)).fold(IO.pure(ExitCode.Error)) { ns =>
          compute(cursor.copy(current = ns.write.value).move(ns.action), ns.toState)
        }
      } yield cp
    }

  def exec(initial: MachineState): IO[ExitCode] =
    for {
      _      <- IO(println(ds))
      blank  <- IO.pure(ds.blank.character.value)
      inp    <- IO.pure(blank*3 + input + blank*3)
      cursor <- IO.pure(Cursor.fromString(inp))
      r      <- compute(cursor.moveRight.moveRight.moveRight, initial)
    } yield r

}
