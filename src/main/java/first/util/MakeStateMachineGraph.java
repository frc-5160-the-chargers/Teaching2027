package first.util;

import java.lang.annotation.Target;

/**
 * Generates a mermaid graph for a state machine encapsulated within a method. Note that this
 * doesn't support multiple state machines per method.
 */
@Target(java.lang.annotation.ElementType.METHOD)
public @interface MakeStateMachineGraph {
  /**
   * An advanced configuration option that allows you to generate diagrams for
   * a custom state machine class sharing the same syntax as WPILib's {@link StateMachine}.
   *
   * @return The fully qualified name of the state machine class.
   */
  String stateMachineType() default "StateMachine";
}
