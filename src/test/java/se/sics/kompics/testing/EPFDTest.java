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

import org.junit.Test;

import se.sics.kompics.Component;
import se.sics.kompics.Init;
import se.sics.kompics.network.Network;
import se.sics.kompics.network.netty.NettyInit;
import se.sics.kompics.network.netty.NettyNetwork;
import se.sics.kompics.testing.fd.*;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timer;

import static se.sics.kompics.testing.Direction.*;

import java.util.Comparator;

public class EPFDTest {
  private ScheduleTimeout st = new ScheduleTimeout(500);
  private EPFD.PingTimeout timeout = new EPFD.PingTimeout(st);

  private TAddress pongerAddr = Util.getPongerAddr();
  private TAddress epfdAddr = Util.getEPFDAddr();
  private Pong pong = new Pong(pongerAddr, epfdAddr);
  private Ping ping = new Ping(epfdAddr, pongerAddr);
  private Suspect suspect = new Suspect(pongerAddr);
  private Restore restore = new Restore(pongerAddr);

  private TestContext<EPFD> tc = TestContext.newTestContext(EPFD.class, Init.NONE);
  private Component ponger = tc.create(Ponger.class, Init.NONE);
  private Component epfd = tc.getComponentUnderTest();

  @Test
  public void mockNetworkAndTimer() {

    tc.setComparator(ScheduleTimeout.class, new ScheduleTimeoutComparator()).
       body();
    // Expect initial SCHED TIMEOUT
    tc.expect(st, epfd.getNegative(Timer.class), OUT);

    // SEND Watch P
    Watch watch = new Watch(pongerAddr);
    tc.trigger(watch, epfd.getPositive(EPFDPort.class)); // WATCH P

    // Send Timeout, Reply with then PING, then SCHEDTIMEOUT
    tc.trigger(timeout, epfd.getNegative(Timer.class)).
    expect(ping, epfd.getNegative(Network.class), OUT).
    expect(st, epfd.getNegative(Timer.class), OUT);

    tc.repeat(30).body().
        // Send Timeout, Reply with Suspect, then PING, then SCHEDTIMEOUT
        trigger(timeout, epfd.getNegative(Timer.class)).
        expect(suspect, epfd.getPositive(EPFDPort.class), OUT).
        expect(ping, epfd.getNegative(Network.class), OUT).
        expect(st, epfd.getNegative(Timer.class), OUT).

        // Send Pong, Send Timeout, Reply with RESTORE, then PING, then SCHEDTIMEOUT
        trigger(pong, epfd.getNegative(Network.class)).
        trigger(timeout, epfd.getNegative(Timer.class)).
        expect(restore, epfd.getPositive(EPFDPort.class), OUT).
        expect(ping, epfd.getNegative(Network.class), OUT).
        expect(st, epfd.getNegative(Timer.class), OUT).
    end();

    //Send Pong, Timeout, Reply with PING, then SCHEDTIMEOUT
    tc.repeat(20).
        disallow(restore, epfd.getPositive(EPFDPort.class), OUT).
        disallow(suspect, epfd.getPositive(EPFDPort.class), OUT).
        body().
        trigger(pong, epfd.getNegative(Network.class)).
        trigger(timeout, epfd.getNegative(Timer.class)).
        expect(ping, epfd.getNegative(Network.class), OUT).
        expect(st, epfd.getNegative(Timer.class), OUT).
    end();

    assert tc.check();
  }

  @Test
  public void mockTimerOnly() {
    // Connect EPFD, Ponger
    Component networkPonger = tc.create(NettyNetwork.class, new NettyInit(pongerAddr));
    Component networkEPFD = tc.create(NettyNetwork.class, new NettyInit(epfdAddr));
    tc.connect(epfd.getNegative(Network.class), networkEPFD.getPositive(Network.class));
    tc.connect(ponger.getNegative(Network.class), networkPonger.getPositive(Network.class));

    tc.
       setTimeout(3000).
        setComparator(ScheduleTimeout.class, new ScheduleTimeoutComparator()).
       disallow(restore, epfd.getPositive(EPFDPort.class), OUT).
       body();

    // Expect initial SCHED TIMEOUT
    tc.expect(st, epfd.getNegative(Timer.class), OUT);

    // SEND Watch P
    Watch watch = new Watch(pongerAddr);
    tc.trigger(watch, epfd.getPositive(EPFDPort.class)); // WATCH P

    // Send Timeout, Reply with then PING, then SCHEDTIMEOUT
    triggerTimeoutExpectPingAndSchedTimeout();

    // Expect Pong, Send Timeout, Reply with PING, then SCHEDTIMEOUT
    tc.expect(pong, epfd.getNegative(Network.class), IN);
    triggerTimeoutExpectPingAndSchedTimeout();

    // drop Pongs within scope, then timeout and suspect
    tc.repeat(10).
            drop(pong, epfd.getNegative(Network.class), IN).
            body().
            trigger(timeout, epfd.getNegative(Timer.class)).
            expect(suspect, epfd.getPositive(EPFDPort.class), OUT). // resuspect
            expect(ping, epfd.getNegative(Network.class), OUT).
            expect(st, epfd.getNegative(Timer.class), OUT);
    tc.end();

    assert tc.check();
  }

  private void triggerTimeoutExpectPingAndSchedTimeout() {
    tc.trigger(timeout, epfd.getNegative(Timer.class)).
    expect(ping, epfd.getNegative(Network.class), OUT).
    expect(st, epfd.getNegative(Timer.class), OUT);
  }

  public EPFDTest() {
    st.setTimeoutEvent(timeout);
  }

  private class ScheduleTimeoutComparator implements Comparator<ScheduleTimeout> {

    @Override
    public int compare(ScheduleTimeout o1, ScheduleTimeout o2) {
      return 0;
    }
  }
}
