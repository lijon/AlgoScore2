/*

WORK IN PROGRESS

todo:

* at t>length or ev.isNil, close all parts that hasn't ended yet (dangling \begin's)

* Now that we have begin/end plot parts:
- possibly auto allocation of vertical space (instead of resetting top:0 manually in plotspecs)
- part name can be given and the part boundaries can be marked graphically in various ways
perhaps we should draw part boundaries and name as a special kind of plotSpec type? \part
then the only thing in the plotPart events added are plotID..

* one problem with drawing baseline at part-end is that it will be drawn on top of the other graphics..
one solution would be to record all pen actions for the other graphics, and play them back only at part-end.
perhaps there are simpler solutions..

* simple western notation
- clef
- stafflines
- accidentals (no key)
- note heads
- stems going to a duration bar
- notes with same duration are grouped into a chord

* the processing in plotSpecs_ setter needs to be done for the defaults too!

* y0 with \levels (for segCtrl mono synth)
- needs to draw valueLabel and dot at first point
- needs to draw tick at last point
- ignore y0 when type is not \levels? and ignore y1 when y0 is set?
perhaps it would make more sense to have a separate type..
Also, both y0, y and y1 might be useful in user-drawing funcs.. so we should always get these?

* curves for line and levels

* move tickLine stuff into the event?
otoh, they can be are drawn between events.. so they must be bound to a plotPart.
perhaps it would make sense to provide a dictionary of plotPart properties to PatternPlotter?

* custom graphics in special cases, we could provide a drawing func and type: \custom?

* time grid, bars:beats and/or seconds, etc.. user can provide a function for creating the strings
minor and major ticks

* pagination for printing, how?
record the pen (see ASPen), then we can iterate it for each page and set a clip box and translation

* representing a composition at macro level, like blocks with different names/colors.
use plotParts..

* plotting Synth outputs ??
for example, the results of different LFNoise's, LFOs, Envelopes, etc..
this would need to happen in realtime.
1. write these to busses and poll these busses continuously lang-side
or
2. use SendTrig to send these to the language

* should we multichannel expand on more than y and y1?
for example, dotSize..
one could also work around it also for fixed y position:
y: \mChKey -> {0.5}

*/

/*************************************************************************
    PatternPlotter - Jonatan Liljedahl <lijon@kymatica.com>

    plot patterns in userview

usage:

    p = PatternPlotter(pattern, plotSpecs);

    where pattern is an event pattern and plotSpecs an array of Event's describing what and how to plot.

    p.bounds    returns the bounds of the plot in pixels, as a Rect with 0@0 as origin
    p.draw(pen) draw the plots. call this inside your userView

    p.gui       create a window with a view that draws the plots

NOTE: p.bounds not working anymore, since plotSpecs are now passed in the events (so that they can change) meaning we don't know the plotSpecs in advance..
not sure how to handle that.

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
    valueLabelNoRepeat if true, only draw value label if it changed from last data point
    plotID      only plot this if the pattern event has a plotID that matches this,
                can be nil to match all (default), or a collection to match several.
                The id itself can be a symbol or number.

  dynamic parameters:

    y           vertical position of data point (in the range 0.0 - 1.0)
    y1          vertical position for end point for type \levels
                y and y1 can multichannel expand if the associated event key returns an array
    lineWidth   line width (pixels)
    padding     top and bottom padding (pixels)
    dotSize     size of data point circle (pixels)
    dotColor    color of data point circle (Color)
    dotShape    \circle or \square (Symbol)
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

  also a plain symbol is the same as \theSymbol -> nil

internal plotSpec keys:

    state       saved state from last data point
    baseline    pixel position of baseline
    usedKeys    a Set of pattern event keys used by this plotSpec
    lastValueString the value label string from last data point

example:

    PatternPlotter(
        Pbind(
            \degree, Pseq([0,3,6,[3,5,6],[4,1],2],inf),
            \amp, Pseq([0.2,0.6,0.4,1],inf),
            \foo, Pwhite(0,10,inf),
            \dur, Pseq([0.5,0.25,1,0.25],inf)
        ),
        [
            (y: \freq -> [250,550,\exp], valueLabel: \freq -> _.round(0.1), dotSize: \amp -> _.linlin(0,1,1,8), dotColor: Color(0,0,0,0.4), \lineWidth:2),
            (y: \amp -> [0,1], type: \bargraph, height: 50, baselineColor: Color.grey, dotShape: \square),
            (y: \foo -> [0,10], dotSize: 3, type: \linear, height: 100, valueLabel: \foo -> nil)
        ]
    ).length_(12).tickFullHeight_(false).gui;

********************************************************************/

PplotPart {
//    *new {|options=(()), pattern|
    *new {|id=\noID, pattern|
//        var id = options.plotID ? \noID;
        ^Pseq([
            (type: \plotPart, scope: \begin, dur: 0),
            pattern,
            (type: \plotPart, scope: \end, dur: 0),
        ]) <> (plotID: id);
    }
}


PatternPlotter {
    var <length, // duration to plot (in seconds)
        <xscale = 50, // pixels per second (time zoom level)
        <leftMargin = 10,
        <rightMargin = 10,
        <>labelMargin = 0, // move to plotSpec
        <>defaultEvent;

    var <>tickColor,
        <>tickDash,
        <>tickTimeTolerance = 0.001,
        <>tickFullHeight = true;

    var <>pattern, <>defaults, <plotSpecs;//, orgPlotSpecs;

    var <bounds;

    classvar mappableSymbols;

//    var drawCount = 0;

    *initClass {
        mappableSymbols = IdentitySet[\y,\y0,\y1,\lineWidth,\dotSize,\dotColor,\valueLabel,\valueLabelColor,\valueLabelFont,\valueLabelOffset,\dash,\color];
    }

    *new {|pattern,defaults,plotSpecs|
        ^super.new.init(pattern, defaults, plotSpecs);
    }

    gui {
        var bnd = (bounds+Rect(0,0,20,20)) & Rect(0,0,800,400);
        var win = Window("Pattern Plot",bnd).front;
        var scr = ScrollView(win, bnd).resize_(5);
        bnd.debug("bnd");
        bounds.debug("bounds");
        UserView(scr,bounds).background_(Color.white).drawFunc_({|v|
            this.draw;
        }).refresh;
        ^win;
    }

    init {|aPattern, aDefaults, aPlotSpecs|
        defaults = (
            y: \freq -> ControlSpec(20,20000,\exp),
            y1: nil,
            height: 150,
            type: \levels,
//            lenKey: \sustain,
            label: nil,
            lineWidth: 1,
            padding: 20,
            dotSize: 2.5,
            dotColor: Color.black,
            dotShape: \circle,
            labelColor: Color(0.3,0.6,0.4),
            labelFont: Font.monospace(9),
            valueLabel: nil,
            valueLabelColor: Color(0.6,0.3,0.4),
            valueLabelFont: Font.monospace(9),
            valueLabelOffset: 4 @ -12,
            valueLabelNoRepeat: false,
            dash: FloatArray[inf,1], //hack
            color: Color.black,
            baselineDash: FloatArray[inf,1],
            baselineColor: nil,
            match: #{|plot,ev| //NOTE: plot (self) is passed automatically
                plot.plotIDs.isNil or: {
                    plot.plotIDs.includes(ev.plotID)
                } and: {plot.filter(ev)}
            },
            filter: #{|plot,ev| true},
        );
        if(aDefaults.notNil) {
            defaults.putAll(aDefaults);
        };
        bounds = Rect(0,0,800,500);
        tickColor = Color(0,0,0.5,0.5);
        tickDash = FloatArray[1,2];
        this.length = 16;
        this.pattern = aPattern.plotPart(\defaultID);
//        if(aPlotSpecs.notNil) {
//            this.pattern = aPattern <> (plotSpecs:aPlotSpecs);
//        };
        this.plotSpecs = aPlotSpecs;
        this.defaultEvent = Event.default;
    }

    parmap {|e,v|
        ^if(v.class===Association) {
            case
                {v.value.isKindOf(AbstractFunction)} {
                    v.value.multiChannelPerform(\value,e[v.key].value,e)
                }
                {v.value.isKindOf(Nil)} {
                    e[v.key].value.asArray
                }
                {
                    v.value.asSpec.unmap(e[v.key].value).asArray
                };
        } {
//            if(v.class===Symbol) {
//                e[v].value.asArray
//            } {
//            if(v.value.isKindOf(AbstractFunction)) {
//                v.multiChannelPerform(\value,e)
//            } {
                [v.value]
//            }
            //            }
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
        var height = 0, maxheight = 0;
//        plotSpecs = aPlotSpecs.reverse;
        plotSpecs = aPlotSpecs;
        plotSpecs.do {|p|
            p.parent = defaults;
            p.top !? { height = p.top };
            height = height + (p.padding*2) + p.height;
            if(height > maxheight) {maxheight = height};
            p.pairsDo {|k,v|
                // better to convert single symbols to key -> nil here
                if(v.class===Symbol and: {mappableSymbols.includes(k)}) {
                    v = (v -> nil);
                    p[k] = v;
                };
                if(v.class===Association) {
                    p.usedKeys = p.usedKeys.add(v.key).as(IdentitySet)
                }

            };
            if(p.plotIDs.notNil) {
                p.plotIDs = p.plotIDs.asArray.as(IdentitySet);
            };
            if(p.label.isNil) {
                p.label = if(p.y.class===Association){p.y.key};
            };
            if(p.lenKey.isNil) {
                p.lenKey = if(p.y0.isNil,\sustain,\dur);
            };
            if((p.y0.class===Association) and: {p.y0.value.isKindOf(Nil)}) {
                p.y0.value = p.y.value;
            };
        };
        bounds.height = maxheight.debug("total height");
    }

    draw {|pen=(Pen)|
        var stream = pattern.asStream;
        var t = 0;
        var x;
        var yofs;
        var ev;

//        var topY= -1, bottomY= inf;
        var topY= inf, bottomY= -1;
        var drawTick = {
            if(bottomY >= 0) {
                pen.line(x@topY,x@bottomY);
                pen.width = 1;
                pen.strokeColor = tickColor;
                pen.lineDash = tickDash;
                pen.stroke;
            }
        };

/*        var lastPlotSpecs = nil;
        var usedPlotSpecs = IdentitySet.new;
*/
/*        yofs = 0;
        plotSpecs.do {|plot|
            var y2;

            plot.top !? { yofs = plot.top };
            yofs = yofs + plot.padding;
            y2 = round(yofs-plot.padding)+0.5;

            plot.label !? {
                pen.font = plot.labelFont;
                pen.color = plot.labelColor;
                pen.stringAtPoint(plot.label,labelMargin@y2);
            };

            plot.baseline = yofs+plot.height+0.5;
            plot.baselineColor !? {
                pen.line(leftMargin@plot.baseline,(length*xscale+leftMargin)@plot.baseline);
                pen.width = 1;
                pen.strokeColor = plot.baselineColor;
                pen.lineDash = plot.baselineDash;
                pen.stroke;
            };

            plot.state = IdentityDictionary.new;
            plot.lastValueString = nil;

            yofs = yofs + plot.height+plot.padding;
        };*/

        // FIXME: at t>length or ev.isNil, close all parts that hasn't ended yet (dangling \begin's)
        while { ev = stream.next(defaultEvent); t<=length and: {ev.notNil} } {
            case
            {ev.isKindOf(SimpleNumber)} { t = t + ev }
            {ev.class===Event} { ev.use {
                var str;
                var id = ev.plotID ? \default;
                var newSpecs = ev.plotSpecs;

                x = round(t * xscale) + 0.5 + leftMargin;

                if(ev.type==\plotPart) {
                    yofs = 0;
                    plotSpecs.do {|plot|
                        var y2;

                        if(ev.scope==\begin) {
                            plot.top !? { yofs = plot.top };
                            yofs = yofs + plot.padding;
                            y2 = round(yofs-plot.padding)+0.5;

                            plot.baseline = yofs+plot.height+0.5; // FIXME: we can move this to plotSpecs_ setter
                            if(plot.match(ev)) { //FIXME: actually we should not allow user to change plot.match, keep it with plotID instead and add another plotFilter
                                plot.label !? {
                                    pen.font = plot.labelFont;
                                    pen.color = plot.labelColor;
                                    pen.stringAtPoint(plot.label,(x+labelMargin)@y2);
                                };
                                plot.state = IdentityDictionary.new;
                                plot.lastValueString = nil;
                                plot.startX = x;
                                x.debug("setting startX");
                            };

                            yofs = yofs + plot.height+plot.padding;
                        } {
                            if(plot.match(ev)) {
                                plot.baselineColor !? {
                                    pen.line(plot.startX@plot.baseline,x@plot.baseline);
                                    pen.width = 1;
                                    pen.strokeColor = plot.baselineColor;
                                    pen.lineDash = plot.baselineDash;
                                    pen.stroke;
                                };
                            };
                        };
                    };
                };

                yofs = 0;

                plotSpecs.do {|plot,i|
                    var h = plot.height;
                    var y, y0, y1, lastP, lastP1, dotSize;
                    var state;
/*                    var doPlot = ev.isRest.not and: {
                        plot.plotIDs.isNil or: {
                            plot.plotIDs.includes(id)
                        }
                    } and: {
                        this.checkKeys(ev,plot)
                    };*/
//                    var doPlot = ev.isRest.not and: {plot.match(ev)} and: {this.checkKeys(ev,plot)};
                    var doPlot = (ev.type != \plotPart) and: ev.isRest.not and: {plot.match(ev)} and: {this.checkKeys(ev,plot)};
                    plot.top !? { yofs = plot.top };
                    yofs = yofs + plot.padding;
//                    if(id.isNil or: {id==plot.plotID} and: {doPlot and: this.checkKeys(ev,plot)}) {
                    if(doPlot) {
//                        y = bounds.height-round(yofs+(this.parmap(ev,plot.y)*h));
//                        y0 = plot.y0 !? {bounds.height-round(yofs+(this.parmap(ev,plot.y0)*h))};
//                        y1 = plot.y1 !? {bounds.height-round(yofs+(this.parmap(ev,plot.y1)*h))} ? y;
                        y = round(yofs+h-(this.parmap(ev,plot.y)*h));
                        y0 = plot.y0 !? {round(yofs+h-(this.parmap(ev,plot.y0)*h))};
                        y1 = plot.y1 !? {round(yofs+h-(this.parmap(ev,plot.y1)*h))} ? y;
                        state = plot.state[id.asSymbol];

                        plot.state[id.asSymbol] = state.size.max(y.size).max(y1.size).collect {|n|
                            var old, p, p1, lw, dotP;

                            old = state !? {state.clipAt(n).old};
                            p1 = (ev[plot.lenKey].value * xscale + x) @ y1.clipAt(n);
                            lw = this.parmapClip(ev,plot.lineWidth,n);

                            if(y0.notNil) {
                                p = if(state.isNil) {
                                    x @ y0.clipAt(n);
                                } {
                                    old
                                };
                                dotP = p1;
                            } {
                                p = x @ y.clipAt(n);
                                dotP = p;
                            };

//                            plot.lenKey.debug("ev lenkey");

                            if(lw.asInteger.odd) { p.y = p.y + 0.5; p1.y = p1.y + 0.5 };

                            pen.strokeColor = this.parmapClip(ev,plot.color,n);
                            pen.width = lw;
                            pen.lineDash = this.parmapClip(ev,plot.dash,n);

                            switch(plot.type,
                                \linear, {
                                    old !? {
                                        pen.line(old, p);
                                        pen.stroke;
                                    };
                                    old = p;
                                },
                                \steps, {
                                    old !? {
                                        pen.line(old, p.x@old.y);
                                        pen.lineTo(p);
                                        pen.stroke;
                                    };
                                    old = p;
                                },
                                \levels, {
//                                    if(plot.connectOld==true and: {old.notNil}) {
//                                        p = old;
//                                    };
                                    if(lastP != p or: {lastP1 != p1}) {
                                        pen.line(p, p1);
                                        pen.stroke;
                                    };
                                    old = p1;
                                },
                                \bargraph, {
                                    if(lastP != p) {
                                        pen.line(p.x @ plot.baseline, p);
                                        pen.stroke;
                                    };
                                    old = nil;
                                },
                                \dots, {
                                    old = nil;
                                }
                            );
                            if(lastP != p) {
//                            if(lastP != dotP) {
                                dotSize = this.parmapClip(ev,plot.dotSize,n);
                                if(dotSize>0) {
                                    pen.fillColor = this.parmapClip(ev,plot.dotColor,n);
                                    switch(this.parmapClip(ev,plot.dotShape,n),
                                        \square, { pen.addRect(Rect.fromPoints(p-dotSize,p+dotSize)) },
                                        { pen.addArc(dotP, dotSize, 0, 2pi) } //default is circle
                                    );
                                    pen.fill;
                                };
                                if(dotSize>0 or: {plot.type != \dots}) {
                                    if(p.y > bottomY) { bottomY = p.y };
                                    if(p.y < topY) { topY = p.y };
                                };
                                if(plot.valueLabel.notNil and:
                                {(str=this.parmapClip(ev,plot.valueLabel,n).asString)!=plot.lastValueString}) {
                                    pen.font = this.parmapClip(ev,plot.valueLabelFont,n);
                                    pen.color = this.parmapClip(ev,plot.valueLabelColor,n);
                                    pen.stringAtPoint(str,dotP + this.parmapClip(ev,plot.valueLabelOffset,n));
                                    if(plot.valueLabelNoRepeat) {plot.lastValueString = str};
                                };
                            };
                            lastP = p;
                            lastP1 = p1;

                            (old:old); // return new state
                            //old;
                        }.clipExtend(y.size);
                    };
                    yofs = yofs + h + plot.padding;
                };

                if(tickFullHeight) {
                    topY = 0; //ofs.neg; //?
                    bottomY = bounds.height; // + ofs.neg; //?
                };
                t = t + ev.delta;
                if(ev.delta > tickTimeTolerance or: tickFullHeight) {
                    drawTick.value;
                    topY= inf;
                    bottomY= -1;
                }
            }} // case event
        }; // event iteration loop
        // draw the last tick
        drawTick.value;
    }
}

+ Pattern {
    plotPart {|id|
        ^PplotPart(id, this)
    }
}