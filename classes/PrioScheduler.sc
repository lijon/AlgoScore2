/*
PrioScheduler - allow control of order between items/routines/patterns when events are scheduled on the same time.

q = PrioScheduler();
myThing.play(q.prio(10));

alternatives:
A. (current) schedule 'this' on tempoclock once for each item scheduled on this.
it means we must avoid scheduling 'this' more than one time at the same time.
we do this by saving current time in an instance var and return early from this.awake if inBeats==current

B. keep only one schedule of 'this' on tempoclock at a time,
let this.awake reschedule itself on tempoclock for next awake time.
we could have a 'first' boolean that only starts the scheduling in schedAbs the first time..
but that breaks if we try to schedule another item with nearer wakeup time.
we could detect this, but how would we remove/unschedule/move the old one? that's not possible at the moment.

EXAMPLES:

(
q = PrioScheduler();
a = Routine {
    inf.do {|i|
        ("A"+i+Main.elapsedTime).postln;
        1.wait;
    }
};
b = Routine {
    inf.do {|i|
        ("B"+i+Main.elapsedTime).postln;
        2.wait;
    }
};
{
    a.play(q.prio(10));
    b.play(q.prio(20));
}.fork(TempoClock);
)

(
q = PrioScheduler();
a = Pbind(
    \type, \dummy,
    \dur, 2,
    \foo, Pfunc { postln("A"+thisThread.beats) }
);
b = Pbind(
    \type, \dummy,
    \dur, 1,
    \foo, Pfunc { postln("B"+thisThread.beats) }
);
{
    a.play(q.prio(10));
    b.play(q.prio(20));
}.fork(TempoClock);
)

*/

PrioScheduler {
    var <clock, <queue, current;

    *new {|clock|
        ^super.new.init(clock);
    }

    init {|clk|
        clock = clk ? TempoClock.default;
        queue = PriorityQueue.new;
        CmdPeriod.add {this.clear};
    }

    prio {|level|
        ^PrioClockWrapper(this,level);
    }

    schedAbs {|time, task, prio=0|
        var top = queue.topPriority;
        queue.put(time, [task, prio]);
        clock.schedAbs(time,this);
    }

    awake {|inBeats, inSeconds, inClock|
        var items = [];
        var dt, task, prio;
        if(inBeats==current) {
            ^nil;
        };
        while { inBeats == queue.topPriority } {
            items = items.add(queue.pop);
        };
        items.sort {|a,b| a[1] < b[1]}.do {|x|
            #task, prio = x;
            dt = task.awake(inBeats,inSeconds,inClock);
            if(dt.isNumber) {
                this.schedAbs(inBeats+dt,task,prio);
            };
        };
        // in case the awakenings scheduled something for this same time..
        // we could also loop here until there are no items with time!=inBeats?
        if(inBeats != queue.topPriority) {
            current = inBeats;
        };
        ^nil;
    }

    clear {|releaseNodes = true|
/*
        if (queue.size > 1) {
            forBy(1, queue.size, 3) {|i|
                queue[i+1].removedFromScheduler(releaseNodes)
            };
        };
*/
        queue.do {|x| x.removedFromScheduler(releaseNodes) };
        queue.clear;
        current = nil;
    }
}

PrioClockWrapper {
    var sched, prio, clock;

    *new {|sched, prio|
        ^super.new.init(sched, prio);
    }
    init {|...args|
        #sched, prio = args;
        clock = sched.clock;
    }

    beats { ^clock.beats }
    tempo { ^clock.tempo }
    seconds { ^clock.seconds }
	secs2beats {|secs| ^secs * this.tempo }
	beats2secs {|inBeats| ^inBeats / this.tempo }

    play {|task, quant = 1|
        //FIXME: handle quant
        this.sched(0, task);
    }
    sched {|delta, task|
        this.schedAbs(clock.beats+delta,task);
    }
    schedAbs {|time, task|
        sched.schedAbs(time,task,prio);
    }
}
