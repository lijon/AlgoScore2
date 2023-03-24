/*

WORK IN PROGRESS

todo:

* at t>length or ev.isNil, close all parts that hasn't ended yet (dangling \begin's)

* rename to ASPatternPlotter and ASPlotPart?

* make use of ASPoint (@*) and add zoom and xscale to gui

* add printing (QPenPrinter) to gui

* one thing we need to think about:
say we have two parallell plotParts: a and b
and a plotSpec that matches both (merges) a and b
if b ends before a, should the plotSpec be closed (plotPart end)? currently it is. but will keep plotting after its end, since a is still running..

possibly it would make more sense to close the plotSpec when all matching plotParts has ended? and open it when any of them has started?

* when getting the full length of a pattern (assuming it ends), we won't know the width in pixels..
perhaps we should use ASPen from the start, and generate the pen instructions when setting the pattern.
instead of "length" we name it "timelimit", which can default to a big value (30 mins or so).
then we can find the biggest x-coordinate used, add the left/right margins and return that as the bounds.width.
p.draw would then just replay it. and we can make use of the x-scaling of ASPoint to have a zoomable plot!
-either p.draw takes a xscale arg, and the caller would need to multiply p.bounds.width accordingly.
-or we set p.xscale and p.bounds would adjust bounds.width accordingly..

* Now that we have begin/end plot parts:
- possibly auto allocation of vertical space (instead of resetting top:0 manually in plotspecs)
- part name can be given and the part boundaries can be marked graphically in various ways
perhaps we should draw part boundaries and name as a special kind of plotSpec type? \part
then the only thing in the plotPart events added are plotID..

* one problem with drawing baseline at part-end is that it will be drawn on top of the other graphics..
one solution would be to record all pen actions for the other graphics, and play them back only at part-end.
perhaps there are simpler solutions..

* multiline labels

* improve the simple western notation
- duration beams and stems, give beam y position?
- handle notehead and accidentals collision?
- setting for accidental font, color and size
- clef. use some font? or try to convert an SVG to pen instructions?
- spelling of accidentals: user gives list of the five accidentals, -1 for flat and +1 for sharp?
- automatic re-spelling of accidentals at collisions in chords?
- white-outs at rests?
- also support alternative 5-line chromatic staff
- marks, symbols, etc..
- gliss (connect from old to p with line)

* \linear2
- needs to draw tick at last point

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
    label       custom label, or nil to use the pattern key of y param
    labelColor  color of label
    labelFont   font of label
    labelOffset offset of label
    baseline    position of baseline (0.0 to 1.0)
    baselineColor color of horizontal bottom line, nil to hide
    baselineDash line dash for horizontal bottom line
    padding     top and bottom padding (pixels)
    valueLabelNoRepeat if true, only draw value label if it changed from last data point
    plotID      only plot this if the pattern event has a plotID that matches this,
                can be nil to match all (default), or a collection to match several.
                The id itself can be a symbol or number.

  dynamic parameters:

    y           vertical position of data point (in the range 0.0 - 1.0)
    y1          vertical position for end point for type \levels
                y and y1 can multichannel expand if the associated event key returns an array
    length      the pattern event key to use for line length in \levels type
    lineWidth   line width (pixels)
    lineHeight  spacing between pitches for type \notes
    octave      octave for staff, 0 is with midinote 60 (C) at first bottom help-line. valid for type \notes only
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

  also a single symbol will convert to \theSymbol -> nil

internal plotSpec keys:

    state       saved state from last data point
    yofs        pixel position of plot
    usedKeys    a Set of pattern event keys used by this plotSpec
    lastValueString the value label string from last data point

example:

    PatternPlotter(
        Pbind(
            \degree, Pseq([0,3,6,[3,5,6],[4,1],2],inf),
            \amp, Pseq([0.2,0.6,0.4,1],inf),
            \foo, Pwhite(0,10,inf),
            \dur, Pseq([0.5,0.25,1,0.25],8)
        ),
        nil,
        [
            (y: \freq -> [250,550,\exp], valueLabel: \freq -> _.round(0.1), dotSize: \amp -> _.linlin(0,1,1,8), dotColor: Color(0,0,0,0.4), \lineWidth:2),
            (y: \amp -> [0,1], type: \bargraph, height: 50, baselineColor: Color.grey, dotShape: \square),
            (y: \foo -> [0,10], dotSize: 3, type: \linear, height: 100, valueLabel: \foo -> nil)
        ]
    ).length_(16).gui;

********************************************************************/

PplotPart {
    *new {|id=\noID, pattern|
        ^Prout({ | ev |
            var a, cleanup;
            cleanup = EventStreamCleanup.new;
			a = (type: \plotPart, isRest: Rest(0), dur: 0, plotID: id, plotCleanupFuncs:List[]);
            cleanup.addFunction(a, { a.plotCleanupFuncs.do(_.value); a.plotCleanupFuncs.clear });
            ev = a.yield;
            ev = Pchain(pattern, (plotID: id)).embedInStream(ev);
            cleanup.exit(ev);
        });
    }
}


PatternPlotter {
    var <length, // duration to plot (in seconds)
        <xscale = 50, // pixels per second (time zoom level)
        <leftMargin = 10,
        <rightMargin = 10,
        <>defaultEvent;

    var <>tickColor,
        <>tickDash,
        <>tickTimeTolerance = 0.001,
        <>tickFullHeight = false;

    var <>pattern, <defaults, <plotSpecs;

    var <bounds;

    var penRecorder;

    classvar mappableSymbols;

    *initClass {
        mappableSymbols = IdentitySet[\y,\y0,\y1,\lineWidth,\dotSize,\dotColor,\valueLabel,\valueLabelColor,\valueLabelFont,\valueLabelOffset,\dash,\color,\length];
    }

    *new {|pattern,defaults,plotSpecs|
        ^super.new.init(pattern, defaults, plotSpecs);
    }

    gui {|scale=1,xzoom=1|
        var rct = Rect(0,0,bounds.width*scale,bounds.height*scale);
        var bnd = (rct+Rect(0,0,20,20)) & Rect(0,0,800,600);
        var win = Window("Pattern Plot",bnd).front;
        var scr = ScrollView(win, bnd).background_(Color.white).resize_(5);
        UserView(scr,rct).drawFunc_({|v|
            Pen.scale(scale,scale);
            this.draw(Pen,xzoom);
        }).refresh;
        ^win;
    }

    init {|aPattern, aDefaults, aPlotSpecs|
        var defs = (
            y: \freq -> ControlSpec(20,20000,\exp),
            y1: nil,
            height: 150,
            type: \levels,
            baseline: 0,
//            lenKey: \sustain,
            length: \sustain -> nil,
            label: nil,
            lineWidth: 1,
            lineHeight: 4, // for \notes
            octave: 0, // for \notes
            beamFixed: false,
            padding: 20,
            dotSize: nil, //2.5,
            dotColor: Color.black,
            dotShape: \circle,
            labelColor: Color(0.3,0.6,0.4),
            labelFont: Font.monospace(9),
            labelOffset: 0 @ -20,
            valueLabel: nil,
            valueLabelColor: Color(0.6,0.3,0.4),
            valueLabelFont: Font.monospace(9),
            valueLabelOffset: 4 @ -12,
            valueLabelNoRepeat: false,
            dash: FloatArray[inf,1], //hack
            color: Color.black,
            baselineDash: FloatArray[inf,1],
            baselineColor: nil,
            filter: #{|plot,ev| true},
            plotID: \noID,
        );
        if(aDefaults.notNil) {
            defs.putAll(aDefaults);
        };
        this.defaults = defs;
        bounds = Rect(0,0,100,100);
        tickColor = Color(0,0,0.5,0.5);
        tickDash = FloatArray[1,2];
        this.length = 16;
        this.pattern = aPattern.plotPart(\noID);
        this.plotSpecs = aPlotSpecs;
        this.defaultEvent = Event.default;
    }

    plotMatchesEvent {|plot,ev|
        ^plot.plotIDs.isNil or: {
            plot.plotIDs.includes(ev.plotID)
        }
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
            [v.value(e)]
//            }
            //            }
        }
    }
    parmapClip {|e, v, n| ^this.parmap(e,v).clipAt(n)}

/*    checkKeys {|ev, plot|
        plot.usedKeys.do {|k| ev[k] ?? {^false} };
        ^true;
    }
*/
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

    processPlotSpec {|p|
        p.pairsDo {|k,v|
            if(v.class===Symbol and: {mappableSymbols.includes(k)}) {
                v = (v -> nil);
                p[k] = v;
            };
//            if(v.class===Association) {
//                p.usedKeys = p.usedKeys.add(v.key).as(IdentitySet)
//            }
        };
        if(p.plotIDs.notNil) {
            p.plotIDs = p.plotIDs.asArray.as(IdentitySet);
        };
//        if((p.y0.class===Association) and: {p.y0.value.isKindOf(Nil)}) {
//            p.y0.value = p.y.value;
//        };
    }

    defaults_ {|aDefaults|
        defaults = aDefaults;
        this.processPlotSpec(defaults);
        // should we also set parent again on plotSpecs here?
    }

    plotSpecs_ {|aPlotSpecs|
        var height = 0, maxheight = 0;
        plotSpecs = aPlotSpecs;
        plotSpecs.do {|p|
            p.parent = defaults;
//            p.usedKeys = defaults.usedKeys.copy;
            p.top !? { height = p.top };
            p.yofs = height+p.padding;
//            p.baseline = height+p.padding+p.height+0.5;
            height = height + (p.padding*2) + p.height;
            if(height > maxheight) {maxheight = height};
            this.processPlotSpec(p);
            if(p.label.isNil) {
                p.label = if(p.y.class===Association){p.y.key};
            };
//            if(p.lenKey.isNil) {
//                p.lenKey = if(p.y0.isNil,\sustain,\dur);
//            };
        };
        bounds.height = maxheight;
    }

    recordDrawing {
        Routine {
            penRecorder = ASPen();
            this.prDraw(penRecorder);
        }.next;
    }

    draw {|pen=(Pen),xscale=1|
        penRecorder ?? { this.recordDrawing };
        penRecorder.replay(pen,xscale);
    }

    drawClef {|ctx,center,lh|
        var scale = lh/4.5;
        ctx.use {
            ctx.translate(center.x-(17*scale),center.y-(42*scale));
            ctx.scale(scale,scale);
            ctx.translate(-32,-4);
            ctx.moveTo(39.708934@63.678683);
            ctx.curveTo(45.890584@70.256984,39.317094@65.77065,41.499606@70.115061);
            ctx.curveTo(53.010333@59.740875,51.19892@70.428558,54.590321@66.367906);
            ctx.lineTo(45.086538@23.171517);
            ctx.curveTo(45.354941@15.049945,44.143281@18.81826,44.851281@16.457097);
            ctx.curveTo(50.873134@10.949208,46.698676@11.295749,50.055822@9.7473042);
            ctx.curveTo(49.256275@20.590821,51.339763@11.635413,52.468042@14.844006);
            ctx.curveTo(34.2417@41.468011,46.751378@25.072835,35.096985@30.950138);
            ctx.curveTo(51.339266@54.71374,33.501282@50.614249,43.075689@57.369301);
            ctx.curveTo(56.258057@40.328987,56.825686@52.950639,59.653965@44.62402);
            ctx.curveTo(46.846564@51.0935,47.29624@28.994371,32.923702@46.341263);
            ctx.curveTo(44.1085@47.852721,45.332604@49.90238,44.300646@48.980054);
            ctx.curveTo(54.294493@50.18735,42.237755@36.876941,58.741182@39.774741);
            ctx.curveTo(40.874269@51.477433,52.466001@54.469045,45.080341@55.297323);
            ctx.curveTo(39.708327@37.687888,37.350853@48.277521,35.787387@42.113231);
            ctx.curveTo(52.954064@18.108736,45.018831@31.694223,51.288782@26.31366);
            ctx.curveTo(44.429027@10.385835,54.923313@8.4061491,48.493821@0.84188926);
            ctx.curveTo(43.863006@22.963534,43.065093@13.588288,42.557016@16.803074);
            ctx.lineTo(51.780549@60.311215);
            ctx.curveTo(49.472374@68.355474,52.347386@62.985028,51.967911@66.664419);
            ctx.curveTo(42.791575@67.770092,48.236187@69.193154,43.861784@69.769668);
            ctx.fill;
            ctx.addArc(43.809425@63.584198,4,0,2pi);
            ctx.fill;
        };
    }

    prDraw {|pen|
        var stream = Pfindur(length,pattern).asStream;
        var t = 0;
        var startTime = thisThread.beats;
        var x;
        var yofs;
        var ev;
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
        var plotEnd = {|plot, x|
            if(plot.isActive == true) {
                if(plot.type==\notes) {
                    var yy = round(plot.height/2+(plot.lineHeight*2.5)+plot.yofs/*-plot.padding*/);
                    var y;
                    var marg = 30;
                    5.do {|i|
                        y = yy-(i*plot.lineHeight*2) + 0.5;
                        pen.line((plot.startX-marg)@y,(x+6)@y);
                    };
//                    pen.line((plot.startX-6)@(yy + 0.5),(plot.startX-6)@y);
                    pen.width = 1;
                    pen.strokeColor = plot.baselineColor ?? {Color.black};
                    pen.lineDash = plot.baselineDash;
                    pen.stroke;
                    y = yy-(plot.lineHeight*2) + 0.5;
                    pen.fillColor = Color.black; // FIXME
                    this.drawClef(pen, (plot.startX - marg + 10) @ y, plot.lineHeight);
                } {
                    var y = round(plot.yofs+(plot.height*(1-plot.baseline))) + 0.5;
                    plot.baselineColor !? {
                        pen.line(plot.startX@y,x@y);
                        pen.width = 1;
                        pen.strokeColor = plot.baselineColor;
                        pen.lineDash = plot.baselineDash;
                        pen.stroke;
                    };
                };
                //plot.isActive = false;
				//"plot stop".postln;
				plot.stop;  // isActive is a method, not a member boolean
            };
        };
        var doHelpLines = {|plot,v,end,op,x,yy|
            var y;
            if(v.perform(op,end)) {
                (v..end).do {|a|
                    if(a.even) {
                        y = round(yy-(a*plot.lineHeight))+0.5;
                        pen.line((x-(2*plot.lineHeight))@y,(x+(2*plot.lineHeight))@y);
                    };
                };
                pen.width = 1;
//                pen.strokeColor=Color.gray(0.5); // FIXME
                pen.strokeColor = plot.baselineColor ?? {Color.black};
                pen.stroke;
            };
        };
        // sharps only
        // var noteLines = [0,0,1,1,2,3,3,4,4,5,5,6];
        // var noteAccidentals = [0,1,0,1,0,0,1,0,1,0,1,0];
        // flats only
        var noteLines = [0,1,1,2,2,3,4,4,5,5,6,6];
        var noteAccidentals = [0,-1,0,-1,0,0,-1,0,-1,0,-1,0];

        while { (ev = stream.next(defaultEvent)).notNil } {
            case
            {ev.isKindOf(SimpleNumber)} { t = t + ev }
            {ev.class===Event} { ev.use {
                var str;
                var id = ev.plotID ? \noID;

                x = round(t * xscale) + 0.5 + leftMargin;
                yofs = 0;

                plotSpecs.do {|plot,i|
                    var h = plot.height;
                    var y, y1, lastP, lastP1;
                    var state;
                    var evMatches = this.plotMatchesEvent(plot,ev);
                    var doPlot = ev.isRest.not and: evMatches and: {plot.filter(ev)};
                    plot.top !? { yofs = plot.top };

                    if(ev.type==\plotPart and: evMatches) {
                        if(plot.isActive!=true) {
                            ev.plotID.debug("plotPart begin [plotspec %]".format(i));
                            // FIXME: only draw label the first time this plotspec is drawn?
                            plot.label !? {
                                pen.font = plot.labelFont;
                                pen.color = plot.labelColor;
//                                pen.stringAtPoint(plot.label,(x+labelMargin)@(round(yofs)+0.5));
                                pen.stringAtPoint(plot.label,x@(yofs+plot.padding) + plot.labelOffset);
                            };
                            // plot.startBarColor !? {
                            //     var w = plot.startBarWidth ? 10;
                            //     pen.addRect(Rect(x-w-10,yofs+plot.padding,w,plot.height));
                            //     pen.fillColor = Color.black;
                            //     pen.fill;
                            // };
                            plot.state = IdentityDictionary.new; // should this be here? I guess so.
                            plot.lastValueString = nil;
                            plot.startX = x;
                            //plot.isActive = true;
							plot.resume; // The above is illegal
                            ev.plotCleanupFuncs.add {
                                plot.plotIDs.debug("plotPart cleanup [plotspec %]".format(i));
                                plotEnd.value(plot, x);
                            };
                        };
                        doPlot = false; // actually not needed..
                    };

                    yofs = yofs + plot.padding;

                    if(doPlot) {
                        y = round(yofs+h-(this.parmap(ev,plot.y)*h));
//                        y0 = plot.y0 !? {round(yofs+h-(this.parmap(ev,plot.y0)*h))};
                        y1 = plot.y1 !? {round(yofs+h-(this.parmap(ev,plot.y1)*h))} ? y;
                        state = plot.state[id.asSymbol];

                        plot.state[id.asSymbol] = state.size.max(y.size).max(y1.size).collect {|n|
                            var old, p, p1, lw, ty, dotSize, type;

                            old = state !? {state.clipAt(n).old};
                            lw = this.parmapClip(ev,plot.lineWidth,n);

                            type = plot.type;
                            if(type==\linear2) {
                                p = (ev.dur * xscale + x) @ y1.clipAt(n);
                                p1 = 0@0;
                                ty = if(old.notNil) {old.y} {topY};
                                type = \linear;
                            } {
                                p = x @ y.clipAt(n);
//                                p1 = (ev[plot.lenKey].value * xscale + x) @ y1.clipAt(n);
                                p1 = (this.parmapClip(ev,plot.length,n) * xscale + x) @ y1.clipAt(n);
                                ty = p.y;
                            };

                            if(lw.asInteger.odd) { p.y = p.y + 0.5; p1.y = p1.y + 0.5 };

                            pen.strokeColor = this.parmapClip(ev,plot.color,n);
                            pen.width = lw;
                            pen.lineDash = this.parmapClip(ev,plot.dash,n);

                            if(plot.include.notNil and: {this.parmapClip(ev,plot.include,n)!=true}) {
                                type = \skip;
                            };

                            switch(type,
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
                                    if(lastP != p or: {lastP1 != p1}) {
                                        pen.line(p, p1);
                                        pen.stroke;
                                    };
                                    old = p1;
                                },
                                \bargraph, {
                                    if(lastP != p) {
                                        pen.line(p.x @ (round(plot.yofs+(plot.height*(1-plot.baseline))) + 0.5), p);
                                        pen.stroke;
                                    };
                                    old = nil;
                                },
                                \dots, {
                                    old = nil;
                                },
                                \notes, {
                                    var m = this.parmapClip(ev,plot.y,n);
                                    var oct = m div: 12;
                                    var line = noteLines[m%12]+(oct-(plot.octave+5) * 7 - 2);
                                    var acc = noteAccidentals[m%12];
                                    var lh = plot.lineHeight;
                                    var yy = round(plot.height/2+(lh*2.5) + yofs /*- plot.padding*/);
                                    var y2 = round(yy-(line*lh)); //+0.5;
                                    var p2 = (this.parmapClip(ev,plot.length,n) * xscale + x) @ round(y2);
                                    var beamPos = plot.beamPosition !? {this.parmapClip(ev,plot.beamPosition,n)} ? 0;
                                    p = x@round(y2); // used by valueLabel..
                                    ty = y2; // used by tick drawing

                                    if(lw.asInteger.odd) { p.y = p.y + 0.5; p2.y = p2.y + 0.5 };
                                    // FIXME: for chords, we should not draw helplines ontop of old noteheads!
                                    // need to save max helpline number in state?
                                    pen.use {
                                        doHelpLines.value(plot,line,-1,'<=',x,yy);
                                        doHelpLines.value(plot,line,10,'>=',x,yy);
                                    };

                                    // FIXME: not relative notehead but middle of staff??
                                    if(beamPos != 0) {
                                        var x2 = round(if(beamPos>0,p.x+(lh*1.1),p.x-(lh*1.1)))+0.5;
//                                        var p3 = x2 @ round(y2-(beamPos*lh)); // relative this note
//                                        var p3 = x2 @ round(yy-(4+beamPos*lh)); // relative middle line
                                        var p3 = if(this.parmapClip(ev,plot.beamFixed,n)==true) {
                                            x2 @ round(yy-(4+beamPos*lh)); // relative middle line
                                        } {
                                            x2 @ round(y2-(beamPos*lh)); // relative this note
                                        };
                                        if(lw.asInteger.odd) { p3.y = p3.y + 0.5 };
                                        pen.use {
                                            pen.width = 1;
                                            pen.line(x2@p.y,p3);
                                            pen.stroke;
                                        };
                                        pen.line((x2-0.5) @ p3.y, p2.x @ p3.y);
                                        pen.stroke;
                                    } {
                                        if(lw>0) {
                                            pen.line(p.x+(lh*2-1) @ p.y,p2); // duration line
                                            pen.stroke;
                                        };
                                    };

                                    pen.addOval(Rect(round(x-(lh*1.1))+0.0,round(y2-lh)+0.5,round(lh*2.2)+1,round(lh*2)));
                                    pen.fillColor = this.parmapClip(ev,plot.color,n);
                                    pen.use {
                                        pen.rotate(-0.4,x,y2);
//                                        pen.skew(0,-0.1);
                                        pen.fill;
                                    };
                                    // FIXME: offset for accidentals are not correct..
                                    // better if we draw them with pen instructions.
                                    case
                                    {acc>0} {
                                        pen.font = Font.serif(lh*3.5); // FIXME
                                        pen.stringInRect("#",Rect(x-13,y2-6,10,20)); // FIXME
                                    }
                                    {acc<0} {
                                        pen.font = Font.serif(lh*3); // FIXME
                                        pen.stringInRect("b",Rect(x-11,y2-6,10,20)); // FIXME
                                    };
                                }
                            );

                            if(type!=\skip) {
                                if(lastP != p) {
                                    dotSize = plot.dotSize !? {this.parmapClip(ev,plot.dotSize,n)} ?? {if(plot.type==\notes,0,2.5)};
                                    if(dotSize>0) {
                                        pen.fillColor = this.parmapClip(ev,plot.dotColor,n);
                                        switch(this.parmapClip(ev,plot.dotShape,n),
                                            \square, { pen.addRect(Rect.fromPoints(p-dotSize,p+dotSize)) },
                                            { pen.addArc(p, dotSize, 0, 2pi) } //default is circle
                                        );
                                        pen.fill;
                                    };
                                    if(plot.valueLabel.notNil and:
                                        {(str=this.parmapClip(ev,plot.valueLabel,n).asString)!=plot.lastValueString}) {
                                        pen.font = this.parmapClip(ev,plot.valueLabelFont,n);
                                        pen.color = this.parmapClip(ev,plot.valueLabelColor,n);
                                        pen.stringAtPoint(str,p + this.parmapClip(ev,plot.valueLabelOffset,n));
                                        if(plot.valueLabelNoRepeat) {plot.lastValueString = str};
                                    };
                                };
                                lastP = p;
                                lastP1 = p1;

                                if(plot.drawTick!=false and: {plot.type != \dots or: {dotSize.notNil and: {dotSize>0}}}) {
                                    if(ty > bottomY) { bottomY = ty };
                                    if(ty < topY) { topY = ty };
                                };
                            };
                            (old:old); // return new state
                        }.clipExtend(y.size); // state collect
                    }; //if doPlot
                    yofs = yofs + h + plot.padding;
                }; //plotSpecs loop

                if(tickFullHeight) {
                    topY = 0;
                    bottomY = bounds.height;
                };
                t = t + ev.delta;
                if(ev.delta > tickTimeTolerance or: tickFullHeight) {
                    drawTick.value;
                    topY= inf;
                    bottomY= -1;
                }
            }}; // case event
            thisThread.beats = startTime + t;
        }; // event iteration loop


        // draw the last tick
        drawTick.value;
    }
}

+ Pattern {
    plotPart {|id|
        ^PplotPart(id, this)
    }
    seed {|seed|
        ^Pseed(Pn(seed,1),this)
    }
}