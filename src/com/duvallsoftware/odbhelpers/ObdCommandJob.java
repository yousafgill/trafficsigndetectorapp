package com.duvallsoftware.odbhelpers;

import pt.lighthouselabs.obd.commands.ObdCommand;

/**
 * This class uses some base code from the OBD Reader application from
 * com.github.pires.obd.reader
 */
public class ObdCommandJob {

  private Long _id;
  private ObdCommand _command;
  private ObdCommandJobState _state;

  public ObdCommandJob(ObdCommand command) {
    _command = command;
    _state = ObdCommandJobState.NEW;
  }

  public Long getId() {
    return _id;
  }

  public void setId(Long id) {
    _id = id;
  }

  public ObdCommand getCommand() {
    return _command;
  }

  /**
   * @return job current state.
   */
  public ObdCommandJobState getState() {
    return _state;
  }

  /**
   * Sets a new job state.
   * 
   * @param the
   *          new job state.
   */
  public void setState(ObdCommandJobState state) {
    _state = state;
  }

  /**
   * The state of the command.
   */
  public enum ObdCommandJobState {
    NEW,
    RUNNING,
    FINISHED,
    EXECUTION_ERROR,
    QUEUE_ERROR
  }

}