package first.robot.sdf

import org.wpilib.command3.Command
import org.wpilib.command3.Coroutine
import org.wpilib.command3.Mechanism
import org.wpilib.command3.Trigger
import org.wpilib.command3.button.CommandGamepad

data class State(val command: Command)

class DoneScope {
    var done: Boolean = false
    var next: State? = null
    var wantsStateMachineExit = false
        private set

    fun exitStateMachine() {
        wantsStateMachineExit = true
    }

    val Trigger.get get() = asBoolean
}

class KtStateMachine(private val name: String): Command {
    private val scope = DoneScope()
    private var initialState: State? = null
    private val stateToActionsMap = mutableMapOf<State, MutableList<DoneScope.() -> Unit>>()

    val Command.asState get() = State(this).also { stateToActionsMap[it] = mutableListOf() }
    operator fun State.invoke(fn: DoneScope.() -> Unit) = stateToActionsMap[this]?.add(fn)
    fun forAll(vararg states: State, fn: DoneScope.() -> Unit) = states.forEach { stateToActionsMap[it]?.add(fn) }

    override fun name() = name
    override fun requirements() = emptySet<Mechanism>()
    override fun run(coro: Coroutine) {
        var currentState = initialState ?: return
        var previouslyRunning = false
        while (true) {
            coro.fork(currentState.command)
            inner@while (true) {
                coro.yield()
                val currentlyRunning = coro.scheduler().isScheduledOrRunning(currentState.command)
                scope.done = !currentlyRunning && previouslyRunning
                previouslyRunning = currentlyRunning
                stateToActionsMap[currentState]?.forEach { fn -> fn(scope) }
                if (scope.wantsStateMachineExit) return
                if (scope.next != null) {
                    currentState = scope.next!!
                    scope.next = null
                    break@inner
                }
            }
        }
    }
}

fun KtStateMachine(name: String, block: KtStateMachine.() -> Unit) = KtStateMachine(name).apply(block)
val cmd get() = Command.noRequirements { while (true) it.yield() }.named("Dummy Cmd")

fun test() {
    val xbox = CommandGamepad(0)
    val stateMachine = KtStateMachine("Test") {
        val shooting = cmd.asState
        val scoring = cmd.asState

        shooting { if (done && xbox.rightBumper().get) next = scoring }
        scoring { if (done) exitStateMachine() }

        forAll(shooting, scoring) {
            if (done) next = scoring
        }
    }
}