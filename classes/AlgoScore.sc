//// (c)2011 Jonatan Liljedahl <lijon@kymatica.com>

AlgoScore {
    var <score;
    // TODO: main class and GUI with score, library, toolbar/menu, etc..
}

ASScore {
    var <items, <clock, <>xscale = 10;
    // TODO: add, remove, play, stop, etc.. and graphics (userview).
    // play uses PrioScheduler and schedules the items with prio according to dependency graph
    add {|item|
        items = items.add(item);
        ^items.size;
    }
}

ASConnection {
    var <>srcObj, <>srcPort, <>destObj, <>destPort;

    *new {|srcObj, srcPort, destObj, destPort|
        ^super.newCopyArgs(srcObj, srcPort, destObj, destPort);
    }

    printOn {|stream|
        super.printOn(stream);
        stream << " ( " << srcObj << ":" << srcPort << " => " << destObj << ":" << destPort << " )";
    }

    transmit {
        destObj.set(destPort, srcObj.get(srcPort));
    }
}

ASScoreItem {
    var <connections, <>startTime, <>length, <properties, <score, <id;

    // TODO: userview, update bounds according to startTime and length..
    // put length in properties? also height?
    // we also need to update stuff when some properties changes..
    // we should be smart and collect multiple changes in a single update when possible,
    // or call update explicitly? each prop could have a "changed" flag, checked and cleared by update.
    // and inputs/outputs methods that return list of symbols(?)

    *new {|score|
        ^super.new.initScoreItem(score);
    }

    initScoreItem {|inScore|
        score = inScore;
        startTime = 0;
        length = 1;
        properties = IdentityDictionary();
        id = score.add(this);
    }

    printOn {|stream|
        super.printOn(stream);
        stream << " ( " << id << " )";
    }

    connect {|srcPort, destObj, destPort|
        var con = ASConnection(this, srcPort, destObj, destPort);
        connections = connections.add(con);
    }

    disconnect {|con|
        connections.remove(con);
    }

    transmit {
        connections.do {|c| c.transmit};
    }

    // called by score scheduler
    awake {|beats, seconds, clock|
        this.start;
        this.transmit;
        clock.sched(length) {this.stop; nil};
        ^nil
    }

    // called from this items userView
    draw {|pen|
        this.graphics.replay(pen, score.xscale);
    }

    // Implement these in subclasses
    start {|clock|}
    stop {}
    set {|port, value| "% set % = %".format(this,port,value).postln; }
    get {|port| "% get %".format(this,port).postln; ^nil}
    graphics {
        // note: subclasses might cache this and have a separate makeGraphics
        var pen = ASPen();
        pen.addRect(Rect.fromPoints(ASPoint(0,0),ASPoint(this.length,10)));
        pen.stroke;
        ^pen;
    }
}

ASPoint : Point {
    scaledPoint {|xscale| ^Point(x*xscale,y)}
}

ASPen {
    classvar selectors;
    var <>data;

/*    replay {|target,xscale=1|
        target = target ? Pen;
        data.do {|msg|
            switch(msg[0],
                \moveToTY, {target.moveTo(msg[1].x*xscale @ msg[1].y)},
                \lineToTY, {target.lineTo(msg[1].x*xscale @ msg[1].y)},
                {target.performMsg(msg)}
            );
        }
    }*/

    replay {|target,xscale=1| //a bit slower but more flexible since it detects ASPoint anywhere
        target = target ? Pen;
        data.do {|msg|
            msg = msg.copy;
            msg.do {|m,i|
                if(m.class==ASPoint) {
                    msg[i] = m.scaledPoint(xscale);
                }
            };
            target.performMsg(msg);
        }
    }

    doesNotUnderstand {|...msg|
        if(selectors.isNil) {
            selectors = Pen.implClass.class.methods.collectAs(_.name,IdentitySet)
//            | IdentitySet[\moveToTY,\lineToTY]
        };
        if(selectors.includes(msg[0])) {
            data = data.add(msg);
        } {
            DoesNotUnderstandError(this, msg[0], msg[1..]).throw;
        }
    }
}

