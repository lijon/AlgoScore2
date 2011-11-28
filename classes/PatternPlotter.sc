/*************************************************************************
    PatternPlotter - Jonatan Liljedahl <lijon@kymatica.com>

    plot patterns in userview

usage:

    p = PatternPlotter(pattern, plotSpecs);

    where pattern is an event pattern and plotSpecs an array of Event's describing what and how to plot.

    p.bounds    returns the bounds of the plot in pixels, as a Rect with 0@0 as origin
    p.draw      draw the plots. call this inside your userView

    p.gui       create a window with a view that draws the plots

properties:

    p.length    duration to plot (seconds)
    p.xscale    time zoom level (pixels per beat)
    p.leftMargin left margin (pixels)
    p.rightMargin left margin (pixels)
    p.tickColor color of vertical event tick lines
    p.tickDash  dash of vertical event tick lines
    p.bounds
    p.tickFullHeight
                if false, draw event tick line only from top data point to bottom data point
    p.pattern   the pattern
    p.plotSpecs the plotSpecs
    p.defaults  default plotSpec

plotSpec keys:

    a plotSpec is an Event in the form (param1: value, param2: value, ...)

  static parameters:

    type        \linear, \steps, \levels, \bargraph, \dots
    height      the height of this plot in pixels
    lenKey      the pattern event key to use for line length in \levels type
    label       custom label, or nil to use the pattern key of y param
    labelColor  color of label
    labelFont   font of label
    baselineColor color of horizontal bottom line, nil to hide
    baselineDash line dash for horizontal bottom line
    plotID      only plot this if the pattern event has a plotID that is nil or matches this

  dynamic parameters:

    y           vertical position of data point (in the range 0.0 - 1.0)
    lineWidth   line width (pixels)
    padding     top and bottom padding (pixels)
    dotSize     size of data point circle (pixels)
    dotColor    color of data point circle (Color)
    dash        line dash (FloatArray)
    color       line color (Color)
    valueLabel  value to print at data point
    valueLabelColor color for above
    valueLabelFont font for above
    valueLabelOffset offset for above relative data point
    
  the dynamic parameters can take a single value:

    value       (like 1.0 or Color.black or anything that responds to .value, like Function or Stream)

  or an Association between a pattern event key and mapping spec/function:

    \keyName -> {|input_value| do_something_and_return_output_value }
    \keyName -> nil (just pass the value through as is)
    \keyName -> anything that responds to .asSpec

    A spec is .unmap'd to the range 0.0 - 1.0

internal plotSpec keys:

    state       saved state from last data point
    baseline    pixel position of baseline
    usedKeys    Set of pattern event keys used by this plotSpec

example:

    PatternPlotter(
        Pbind(
            \degree, Pseq([0,3,6,[3,5,6],[4,1],2],inf),
            \amp, Pseq([0.2,0.6,0.4,1],inf),
            \foo, Pwhite(0,10,inf),
            \dur, Pseq([0.5,0.25,1,0.25],inf)
        ),
        [
            (y: \freq -> [250,550,\exp], valueLabel: \freq -> _.round(0.1), dotSize: \amp -> _.linlin(0,1,1,8), dotColor: Color(0,0,0,0.4), \lineWidth:3),
            (y: \amp -> [0,1], type: \bargraph, height: 50, baselineColor: Color.grey),
            (y: \foo -> [0,10], dotSize: 3, type: \linear, height: 100, valueLabel: \foo -> nil)
        ]
    ).length_(12).tickFullHeight_(false).gui;

********************************************************************/

PatternPlotter {
    var <length, // duration to plot (in seconds)
        <xscale = 50, // time to pixels factor (time zoom level)
        <leftMargin = 10,
        <rightMargin = 10,
        <>labelMargin = 10;

    var <>tickColor,
        <>tickDash,
        <>tickFullHeight = true;

    var <>pattern, <>defaults, <plotSpecs;

    var <bounds;

    *new {|pattern,plotSpecs|
        ^super.new.init(pattern, plotSpecs);
    }

    gui {
        var win;
        UserView(win = Window("Pattern Plot",bounds).front,bounds).background_(Color.white).drawFunc_({|v|
            this.draw;
        }).refresh;
        ^win;
    }

    init {|aPattern, aPlotSpecs|
        defaults = (
            y: \freq -> ControlSpec(20,20000,\exp),
            height: 200,
            type: \levels,
            lenKey: \sustain,
            label: nil,
            lineWidth: 1,
            padding: 20,
            dotSize: 2,
            dotColor: Color.black,
            labelColor: Color(0.3,0.6,0.4),
            labelFont: Font.monospace(9),
            valueLabel: nil,
            valueLabelColor: Color(0.6,0.3,0.4),
            valueLabelFont: Font.monospace(9),
            valueLabelOffset: 4 @ -12,
            dash: FloatArray[1,0],
            color: Color.black,
            baselineDash: FloatArray[1,0],
            baselineColor: nil
        );
        bounds = Rect(0,0,0,0);
        tickColor = Color(0,0,0.5,0.5);
        tickDash = FloatArray[1,2];
        this.length = 16;
        this.pattern = aPattern;
        this.plotSpecs = aPlotSpecs;
    }

    parmap {|e,v|
        ^if(v.class==Association) {
            case
                {v.value.isKindOf(AbstractFunction)} {
                    v.value.multiChannelPerform(\value,e[v.key].value)
                }
                {v.value.isKindOf(Nil)} {
                    e[v.key].value.asArray
                }
                {
                    v.value.asSpec.unmap(e[v.key].value).asArray
                };
        } {
            [v.value]
        }
    }
    parmapClip {|e, v, n| ^this.parmap(e,v).clipAt(n)}

    checkKeys {|ev, plot|
        plot.usedKeys.do {|k| ev[k] ?? {^false} };
        ^true;
    }

    calcWidth {
        bounds.width = length*xscale+leftMargin+rightMargin;
    }

    length_ {|len|
        length = len;
        this.calcWidth;
    }
    xscale_ {|val|
        xscale = val;
        this.calcWidth;
    }
    leftMargin_ {|val|
        leftMargin = val;
        this.calcWidth;
    }
    rightMargin_ {|val|
        rightMargin = val;
        this.calcWidth;
    }

    plotSpecs_ {|aPlotSpecs|
        var height = 0;
        plotSpecs = aPlotSpecs.reverse;
        plotSpecs.do {|p|
            p.parent = defaults;
            height = height + (p.padding*2) + p.height;
            p.do {|v|
                if(v.class==Association) {
                    p.usedKeys = p.usedKeys.add(v.key).as(IdentitySet)
                }
            }
        };
        bounds.height = height;
    }

    draw {
        var stream = pattern.asStream;
        var t = 0;
        var x;
        var yofs = 0;
        var ev;
        plotSpecs.do {|plot|
            var y2;
            var lbl = plot.label ?? {if(plot.y.class==Association) {plot.y.key}};
            yofs = yofs + plot.padding;
            y2 = round(bounds.height-yofs-plot.height-plot.padding)+0.5;

            lbl !? {
                Pen.font = plot.labelFont;
                Pen.color = plot.labelColor;
                Pen.stringAtPoint(lbl,(labelMargin)@y2); // print label in plot
            };

            plot.baseline = bounds.height - yofs + 0.5;
            plot.baselineColor !? {
                Pen.line(leftMargin@plot.baseline,(length*xscale+leftMargin)@plot.baseline);
                Pen.width = 1;
                Pen.strokeColor = plot.baselineColor;
                Pen.lineDash = plot.baselineDash;
                Pen.stroke;
            };

            plot.state = nil;

            yofs = yofs + plot.height+plot.padding;
        };

        while { ev = stream.next(Event.default); t<length and: {ev.notNil} } {
            case
            {ev.isKindOf(SimpleNumber)} { t = t + ev }
            {ev.class==Event} { ev.use {
                var topY= -1, bottomY= inf;
                var id = ev.plotID;
                var doPlot = not(ev.type==\rest or: {ev.detunedFreq.value.isRest});

                yofs = 0;
                x = round(t * xscale) + 0.5 + leftMargin;

                plotSpecs.do {|plot|
                    var h = plot.height;
                    var y, lastDot, dotSize;

                    yofs = yofs + plot.padding;
                    if(id.isNil or: {id==plot.plotID} and: {doPlot and: this.checkKeys(ev,plot)}) {
                        y = (bounds.height-round(yofs+(this.parmap(ev,plot.y)*h))+0.5);

                        plot.state = max(plot.state.size,y.size).collect {|n|
                            var old = plot.state !? {plot.state.clipAt(n)};
                            var p = x @ y.clipAt(n);

                            Pen.strokeColor = this.parmapClip(ev,plot.color,n);
                            Pen.width = this.parmapClip(ev,plot.lineWidth,n);
                            Pen.lineDash = this.parmapClip(ev,plot.dash,n);

                            switch(plot.type,
                                \linear, {
                                    old !? {
                                        Pen.line(old, p);
                                        Pen.stroke;
                                    };
                                    old = p;
                                },
                                \steps, {
                                    old !? {
                                        Pen.line(old, old.x@p.y);
                                        Pen.lineTo(p);
                                        Pen.stroke;
                                    };
                                    old = p;
                                },
                                \levels, {
                                    if(lastDot != p) {
                                        Pen.line(p, p + ((ev[plot.lenKey].value * xscale) @ 0));
                                        Pen.stroke;
                                    };
                                    old = nil;
                                },
                                \bargraph, {
                                    if(lastDot != p) {
                                        Pen.line(p.x @ plot.baseline, p);
                                        Pen.stroke;
                                    };
                                    old = nil;
                                },
                                \dots, {
                                    old = nil;
                                }
                            );
                            dotSize = this.parmapClip(ev,plot.dotSize,n);
                            if(lastDot != p) {
                                if(dotSize>0) {
                                    Pen.fillColor = this.parmapClip(ev,plot.dotColor,n);
                                    Pen.addArc(p, dotSize, 0, 2pi);
                                    Pen.fill;
                                };
                                if(plot.valueLabel.notNil) {
                                    Pen.font = this.parmapClip(ev,plot.valueLabelFont,n);
                                    Pen.color = this.parmapClip(ev,plot.valueLabelColor,n);
                                    Pen.stringAtPoint(this.parmapClip(ev,plot.valueLabel,n).asString,p + plot.valueLabelOffset);
                                };
                            };
                            lastDot = p;
                            if(p.y < bottomY) { bottomY = p.y };
                            if(p.y > topY) { topY = p.y };

                            old;
                        }.clipExtend(y.size);
                    };
                    yofs = yofs + h + plot.padding;
                };

                if(tickFullHeight) {
                    topY = 0;
                    bottomY = bounds.height;
                };
                if(topY >= 0) {
                    Pen.line(x@topY,x@bottomY);
                    Pen.width = 1;
                    Pen.strokeColor = tickColor;
                    Pen.lineDash = tickDash;
                    Pen.stroke;
                };
                t = t + ev.delta;
            }
        }}
    }
}

