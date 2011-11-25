PatternPlotter {
    var <length, // duration to plot (in seconds)
        <>xscale = 50, // time to pixels factor (time zoom level)
        <>xmargin = 10;

    var <>tickColor,
        <>tickDash,
        <>tickFullHeight = false;

    var <>pattern, <>defaults, <plotSpec;

    var <bounds;

    *new {|pattern,plotSpec|
        ^super.new.init(pattern, plotSpec);
    }
    
    gui {
        var win;
        var p = PatternPlotter(pattern,plotSpec);
        UserView(win = Window("Pattern Plot",p.bounds).front,p.bounds).background_(Color.white).drawFunc_({|v|
            p.draw;
        }).refresh;
        ^win;
    }
    
    init {|aPattern, aPlotSpec|
        defaults = (
            key: \freq,
            height: 100,
            spec: ControlSpec(20,20000,\exp),
            type: \levels,
            lenKey: \sustain,
            label: nil,
            lineWidth: 1,
            padding: 20,
            dotSize: 2,
            dotColor: Color.black,
            labelColor: Color(0.3,0.6,0.4),
            dash: FloatArray[1,0],
            color: Color.black
        );
        bounds = Rect(0,0,0,0);
        tickColor = Color.black.alpha_(0.5);
        tickDash = FloatArray[1,2];
        this.length = 16;
        this.pattern = aPattern;
        this.plotSpec = aPlotSpec;
    }

    parmap {|e,v|
        ^if(v.class==Association) {
            if(v.value.isKindOf(Function)) {
                v.value.value(e[v.key]).value
            } {
                v.value.asSpec.unmap(e[v.key].value)
            }
        } {
            v ? 0 // ?
        }
    }

    length_ {|len|
        length = len;
        bounds.width = length*xscale+(xmargin*2);
    }

    plotSpec_ {|aPlotSpec|
        var height = 0;
        plotSpec = aPlotSpec;
        plotSpec.do {|p|
            p.parent = defaults;
            height = height + (p.padding*2) + p.height;
        };
        plotSpec = plotSpec.reverse;
        bounds.height = height;
    }

    draw {
        var stream = pattern.asStream;
        var t = 0;
        var last = IdentityDictionary.new;
        var x;
        var yofs = 0;
        plotSpec.do {|plot|
            var y2;
            var lbl = plot.label ?? {if(plot.y.class==Association) {plot.y.key}};
            yofs = yofs + plot.padding;
            y2 = round(bounds.height-yofs-plot.height-plot.padding)+0.5;
            
            lbl !? {
            Pen.font = Font.monospace(9);
                Pen.color = plot.labelColor;
                Pen.stringAtPoint(lbl,(xmargin+2)@y2); // print label in plot
            };
            
    /*        Pen.line(xmargin@y2,(length*xscale+xmargin)@y2);
            Pen.width = 1;
            Pen.strokeColor = Color.grey(0.5);
            Pen.stroke;*/
            yofs = yofs + plot.height+plot.padding;
        };

        while { t<length } {
            stream.next(Event.default).use {|ev|
                var lastP=0@inf, firstP=0@0;
                yofs = 0;
                x = round(t * xscale) + 0.5 + xmargin;

                plotSpec.do {|plot,i|
                    var old = last[i];
                    var h = plot.height;
                    var dotSize;
                    var y;

                    yofs = yofs + plot.padding;
    //                y = ();

                    // the inner function is flopped for multichannel expansion
                    last[i] = {|old,p|
                        if(i==0 and: {p.y > firstP.y}) { firstP = p };

                        Pen.strokeColor = this.parmap(ev,plot.color);
                        Pen.width = this.parmap(ev,plot.lineWidth);
                        Pen.lineDash = this.parmap(ev,plot.dash);

                        switch(plot.type,
                            \linear, {
                                if(old.isNil) {
                                    Pen.moveTo(p);
                                } {
                                    Pen.line(old,p);
                                };
                                Pen.stroke;
                                old = p;
                            },
                            \steps, {
                                if(old.isNil) {
                                    Pen.moveTo(p);
                                } {
                                    Pen.line(old,p);
                                };
                                Pen.lineTo(old = p + (round(ev.delta * xscale) @ 0));
                                Pen.stroke;
                            },
                            \levels, {
                                Pen.line(p, p + ((ev[plot.lenKey].value * xscale) @ 0));
                                Pen.stroke;
                            }
                        );
                        dotSize = this.parmap(ev,plot.dotSize);
                        if(dotSize>0) {
                            Pen.fillColor = this.parmap(ev,plot.dotColor);
                            Pen.addArc(p, dotSize, 0, 2pi);
                            Pen.fill;
                        };
                        if(p.y < lastP.y) {lastP = p};
                        old;
                    }.flop.value(
                        old,
                        Point.multiChannelPerform(\new,
                            x,
                            bounds.height-round(yofs+(this.parmap(ev,plot.y)*h))+0.5
                        )
                    );
                    yofs = yofs + h + plot.padding;
                };

                if(tickFullHeight) {
                    Pen.line(x@0,x@bounds.height);
                } {
                    Pen.line(firstP,lastP);
                };
                Pen.width = 1;
                Pen.strokeColor = tickColor;
                Pen.lineDash = tickDash;
                Pen.stroke;

                t = t + ev.delta;
            }
        }
    }
}

