@file:JvmMultifileClass
@file:JvmName("Workflows")

package com.squareup.workflow1

import com.squareup.workflow1.WorkflowAction.Companion.toString

/**
 * An atomic operation that updates the state of a [Workflow], and also optionally emits an output.
 */
public abstract class WorkflowAction<in PropsT, StateT, out OutputT> {

  /**
   * The context for calls to [WorkflowAction.apply]. Allows the action to set the
   * [state], and to emit the [setOutput].
   *
   * @param state the state that the workflow should move to. Default is the current state.
   */
  public inner class Updater(
    public val props: @UnsafeVariance PropsT,
    public var state: StateT
  ) {
    internal var maybeOutput: WorkflowOutput<@UnsafeVariance OutputT>? = null
      private set

    @Deprecated("Use state instead.", ReplaceWith("state"))
    public var nextState: StateT
      get() = state
      set(value) {
        state = value
      }

    /**
     * Sets the value the workflow will emit as output when this action is applied.
     * If this method is not called, there will be no output.
     */
    public fun setOutput(output: @UnsafeVariance OutputT) {
      this.maybeOutput = WorkflowOutput(output)
    }
  }

  /**
   * Executes the logic for this action, including any side effects, updating [state][StateT], and
   * setting the [OutputT] to emit.
   */
  public abstract fun Updater.apply()

  public companion object {
    /**
     * Returns a [WorkflowAction] that does nothing: no output will be emitted, and
     * the state will not change.
     *
     * Use this to, for example, ignore the output of a child workflow or worker.
     */
    @Suppress("UNCHECKED_CAST")
    public fun <PropsT, StateT, OutputT> noAction(): WorkflowAction<PropsT, StateT, OutputT> =
      NO_ACTION as WorkflowAction<Any?, StateT, OutputT>

    /**
     * Convenience function that returns a [WorkflowAction] that will just set the state to
     * [newState] (without considering the current state) and optionally emit an output.
     */
    @Deprecated(
        message = "Use action",
        replaceWith = ReplaceWith(
            expression = "action {\n" +
                "  state = newState\n" +
                "  emittingOutput?.let(::setOutput)\n" +
                "}",
            imports = arrayOf("com.squareup.workflow1.action")
        )
    )
    public fun <StateT, OutputT> enterState(
      newState: StateT,
      emittingOutput: OutputT? = null
    ): WorkflowAction<Any?, StateT, OutputT> =
      action({ "enterState($newState, $emittingOutput)" }) {
        state = newState
        emittingOutput?.let(::setOutput)
      }

    /**
     * Convenience function that returns a [WorkflowAction] that will just set the state to
     * [newState] (without considering the current state) and optionally emit an output.
     */
    @Deprecated(
        message = "Use action",
        replaceWith = ReplaceWith(
            expression = "action {\n" +
                "  state = newState\n" +
                "  emittingOutput?.let(::setOutput)\n" +
                "}",
            imports = arrayOf("com.squareup.workflow1.action")
        )
    )
    public fun <StateT, OutputT> enterState(
      name: String,
      newState: StateT,
      emittingOutput: OutputT? = null
    ): WorkflowAction<Any?, StateT, OutputT> =
      action({ "enterState($name, $newState, $emittingOutput)" }) {
        state = newState
        emittingOutput?.let(::setOutput)
      }

    /**
     * Convenience function to implement [WorkflowAction] without returning the output.
     */
    @Deprecated(
        message = "Use action",
        replaceWith = ReplaceWith(
            expression = "action {\n" +
                "  state = modify(state)\n" +
                "  emittingOutput?.let(::setOutput)\n" +
                "}",
            imports = arrayOf("com.squareup.workflow1.action")
        )
    )
    public fun <StateT, OutputT> modifyState(
      name: () -> String,
      emittingOutput: OutputT? = null,
      modify: (StateT) -> StateT
    ): WorkflowAction<Any?, StateT, OutputT> =
      action({ "modifyState(${name()}, $emittingOutput)" }) {
        state = modify(state)
        emittingOutput?.let(::setOutput)
      }

    /**
     * Convenience function to implement [WorkflowAction] without changing the state.
     */
    @Deprecated(
        message = "Use action",
        replaceWith = ReplaceWith(
            expression = "action { setOutput(output) }",
            imports = arrayOf("com.squareup.workflow1.action")
        )
    )
    public fun <StateT, OutputT> emitOutput(output: OutputT): WorkflowAction<Any?, StateT, OutputT> =
      action({ "emitOutput($output)" }) { setOutput(output) }

    /**
     * Convenience function to implement [WorkflowAction] without changing the state.
     */
    @Deprecated(
        message = "Use action",
        replaceWith = ReplaceWith(
            expression = "action { setOutput(output) }",
            imports = arrayOf("com.squareup.workflow1.action")
        )
    )
    public fun <PropsT, StateT, OutputT> emitOutput(
      name: String,
      output: OutputT
    ): WorkflowAction<PropsT, StateT, OutputT> =
      action({ "emitOutput($name, $output)" }) { setOutput(output) }

    private val NO_ACTION = object : WorkflowAction<Any?, Any?, Any?>() {
      override fun toString(): String = "WorkflowAction.noAction()"

      override fun Updater.apply() {
        // Noop
      }
    }
  }
}

/**
 * Creates a [WorkflowAction] from the [apply] lambda.
 * The returned object will include the string returned from [name] in its [toString].
 *
 * If defining actions within a [StatefulWorkflow], use the [StatefulWorkflow.workflowAction]
 * extension instead, to do this without being forced to repeat its parameter types.
 *
 * @param name A string describing the update for debugging.
 * @param apply Function that defines the workflow update.
 *
 * @see StatelessWorkflow.action
 * @see StatefulWorkflow.action
 */
public inline fun <PropsT, StateT, OutputT> action(
  name: String = "",
  crossinline apply: WorkflowAction<PropsT, StateT, OutputT>.Updater.() -> Unit
): WorkflowAction<PropsT, StateT, OutputT> = action({ name }, apply)

/**
 * Creates a [WorkflowAction] from the [apply] lambda.
 * The returned object will include the string returned from [name] in its [toString].
 *
 * If defining actions within a [StatefulWorkflow], use the [StatefulWorkflow.workflowAction]
 * extension instead, to do this without being forced to repeat its parameter types.
 *
 * @param name Function that returns a string describing the update for debugging.
 * @param apply Function that defines the workflow update.
 *
 * @see StatelessWorkflow.action
 * @see StatefulWorkflow.action
 */
public inline fun <PropsT, StateT, OutputT> action(
  crossinline name: () -> String,
  crossinline apply: WorkflowAction<PropsT, StateT, OutputT>.Updater.() -> Unit
): WorkflowAction<PropsT, StateT, OutputT> = object : WorkflowAction<PropsT, StateT, OutputT>() {
  override fun Updater.apply() = apply.invoke(this)

  override fun toString(): String = "WorkflowAction(${name()})@${hashCode()}"
}

/** Applies this [WorkflowAction] to [state]. */
@ExperimentalWorkflowApi
public fun <PropsT, StateT, OutputT> WorkflowAction<PropsT, StateT, OutputT>.applyTo(
  props: PropsT,
  state: StateT
): Pair<StateT, WorkflowOutput<OutputT>?> {
  val updater = Updater(props, state)
  updater.apply()
  return Pair(updater.state, updater.maybeOutput)
}

/** Wrapper around a potentially-nullable [OutputT] value. */
public class WorkflowOutput<out OutputT>(public val value: OutputT) {
  override fun toString(): String = "WorkflowOutput($value)"

  override fun equals(other: Any?): Boolean = when {
    this === other -> true
    other !is WorkflowOutput<*> -> false
    else -> value == other.value
  }

  override fun hashCode(): Int = value.hashCode()
}
