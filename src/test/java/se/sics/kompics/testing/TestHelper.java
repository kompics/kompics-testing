/**
 * This file is part of the Kompics Testing runtime.
 *
 * Copyright (C) 2017 Swedish Institute of Computer Science (SICS)
 * Copyright (C) 2017 Royal Institute of Technology (KTH)
 *
 * Kompics is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package se.sics.kompics.testing;

import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Direct;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.KompicsEvent;
import se.sics.kompics.Negative;
import se.sics.kompics.PortType;
import se.sics.kompics.Positive;
import se.sics.kompics.Request;
import se.sics.kompics.Response;

import java.util.Comparator;

public class TestHelper {

  final Direction IN = Direction.IN;
  final Direction OUT = Direction.OUT;


  Ping ping(int id) {
    return new Ping(id);
  }

  Pong pong(int id) {
    return new Pong(id);
  }

  DirectPing dPing(int id) {
    return new DirectPing(id);
  }

  DirectPong dPong(int id) {
    return new DirectPong(id);
  }

  public static class Pinger extends ComponentDefinition {
    Counter pongsReceived;

    public Pinger() {}

    public Pinger(PingerInit init) {
      this.pongsReceived = init.counter;
    }

    Positive<PingPongPort> pingPongPort = requires(PingPongPort.class);

    Handler<Pong> pongHandler = new Handler<Pong>() {
      @Override
      public void handle(Pong event) {
        incrementPongsReceived();
        checkThrowException(event);
      }
    };

    Handler<DirectPong> directPongHandler = new Handler<DirectPong>() {
      @Override
      public void handle(DirectPong event) {
        incrementPongsReceived();
      }
    };

    private void incrementPongsReceived() {
      if (pongsReceived != null) {
        pongsReceived.count++;
      }
    }

    private void checkThrowException(Pong pong) {
      if (pong.id < 0) {
        throw new IllegalStateException("Negative Pong");
      }
    }

    {
      subscribe(pongHandler, pingPongPort);
      subscribe(directPongHandler, pingPongPort);
    }
  }

  public static class Ponger extends ComponentDefinition {
    Counter pingsReceived;

    public Ponger() {}

    public Ponger(PongerInit init) {
      this.pingsReceived = init.counter;
    }

    Negative<PingPongPort> pingPongPort = provides(PingPongPort.class);

    Handler<Ping> pingHandler = new Handler<Ping>() {
      @Override
      public void handle(Ping event) {
        incrementPingsReceived();
      }
    };

    Handler<DirectPing> directPingHandler = new Handler<DirectPing>() {
      @Override
      public void handle(DirectPing ping) {
        answer(ping, new DirectPong(ping.id));
        incrementPingsReceived();
      }
    };

    private void incrementPingsReceived() {
      if (pingsReceived != null) {
        pingsReceived.count++;
      }
    }

    {
      subscribe(pingHandler, pingPongPort);
      subscribe(directPingHandler, pingPongPort);
    }
  }

  public static class PingerInit extends Init<Pinger> {
    Counter counter;
    PingerInit(Counter counter) {
      this.counter = counter;
    }
  }

  public static class PongerInit extends Init<Ponger> {
    Counter counter;
    PongerInit(Counter counter) {
      this.counter = counter;
    }
  }

  public static class PingPongPort extends PortType {
    {
      request(Ping.class);
      request(PingRequest.class);
      request(DirectPing.class);
      indication(Pong.class);
      indication(PongResponse.class);
      indication(DirectPong.class);
    }
  }

  static class Ping implements KompicsEvent {
    int id;
    Ping(int id) {
      this.id = id;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Ping && id - ((Ping) o).id == 0;
    }

    @Override
    public String toString() {
      return "Ping<" + id + ">";
    }
  }

  static class Pong implements KompicsEvent {
    int id;
    Pong(int id) {
      this.id = id;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Pong && id - ((Pong) o).id == 0;
    }

    @Override
    public String toString() {
      return "Pong<" + id + ">";
    }
  }

  static class PingRequest extends Request {
    int id;
    PingRequest(int id) {
      this.id = id;
    }
  }

  static class PongResponse extends Response {
    int id;
    protected PongResponse(int id, PingRequest ping) {
      super(ping);
      this.id = id;
    }
  }

  static class DirectPing extends Direct.Request<DirectPong> {
    int id;
    DirectPing(int id) {
      this.id = id;
    }

    @Override
    public String toString() {
      return "Ping<" + id + ">";
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof DirectPing && id - ((DirectPing) o).id == 0;
    }
  }

  static class DirectPong implements Direct.Response {
    int id;
    DirectPong(int id) {
      this.id = id;
    }

    @Override
    public String toString() {
      return "Pong<" + id + ">";
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof DirectPong && id - ((DirectPong) o).id == 0;
    }
  }

  class Counter { int count; }

  Comparator<Ping> pingComparator = new Comparator<Ping>() {
    @Override
    public int compare(Ping p1, Ping p2) {
      return p1.id - p2.id;
    }
  };

  Comparator<Pong> pongComparator = new Comparator<Pong>() {
    @Override
    public int compare(Pong p1, Pong p2) {
      return p1.id - p2.id;
    }
  };
}
