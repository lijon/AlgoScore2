/*
(
p = PatternController {|f=200, a=0.5|
    Pbind(
        \freq, f,
        \amp, a,
        \dur, Pseq([0.25,0.25,0.5],inf)
    )
};
)
p.play

p.set(\f, Pseq([200,300,400,500],inf))
p.set(\f, 300)
p.set(\a, Pwhite(0.3,0.7,inf))
p.set(\f, Pwhite(100,700,inf))
p.inputs // returns [\f, \a]
p.values

p.stop


(
p = PatternController {|f=200, a=0.5|
    Ppar([
        Pbind(
            \freq, f,
            \amp, a,
            \dur, Pseq([0.25,0.25,0.5],inf)
        ),
        Pbind(
            \freq, f * 2,
            \amp, a,
            \dur, Pseq([0.125,0.125,0.25],inf)
        )
    ])
};
)
p.play

p.set(\f, Pseq([200,300,400,500],inf)/2)
p.set(\a, 0.4)
p.set(\f, Pwhite(100,700,inf).round(50))

x = Pseq([1,2,3,4],inf).asStream
y = x.copy
x.next
y.next

// in some cases one must wrap it in a Pfunc and use arg.source:
(
p = PatternController {|f=2|
    Pbind(
        \freq, Pwrand([100,200,300],Pfunc{[1,f.source,3].normalizeSum},inf),
        \dur, Pseq([0.25,0.25,0.5],inf)
    )
};
)
p.play

p.set(\f, 7)


*/

PatternController {
    var values, <pattern, <player, <inputs;

    *new {|fun|
        ^super.new.init(fun);
    }

    init {|fun|
        values = IdentityDictionary.new;
        inputs = fun.argNames;
        inputs.do {|name,i|
            values[name] = PatternProxy(fun.def.prototypeFrame[i]);
        };
        pattern = fun.valueArray(
            inputs.collect {|name|
                values[name]
            }
        );
    }

    play {
        player = pattern.play;
    }
    
    stop {
        player.stop;
    }

    set {|...args|
        args.pairsDo {|k,v| values[k].source=v};
    }
}

