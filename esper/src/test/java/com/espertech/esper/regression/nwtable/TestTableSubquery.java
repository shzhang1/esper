/*
 * *************************************************************************************
 *  Copyright (C) 2006-2015 EsperTech, Inc. All rights reserved.                       *
 *  http://www.espertech.com/esper                                                     *
 *  http://www.espertech.com                                                           *
 *  ---------------------------------------------------------------------------------- *
 *  The software in this package is published under the terms of the GPL license       *
 *  a copy of which has been included with this distribution in the license.txt file.  *
 * *************************************************************************************
 */

package com.espertech.esper.regression.nwtable;

import com.espertech.esper.client.EPServiceProvider;
import com.espertech.esper.client.EPServiceProviderManager;
import com.espertech.esper.client.EventBean;
import com.espertech.esper.client.scopetest.EPAssertionUtil;
import com.espertech.esper.client.scopetest.SupportUpdateListener;
import com.espertech.esper.metrics.instrumentation.InstrumentationHelper;
import com.espertech.esper.support.bean.SupportBean;
import com.espertech.esper.support.bean.SupportBean_S0;
import com.espertech.esper.support.bean.SupportBean_S1;
import com.espertech.esper.support.client.SupportConfigFactory;
import junit.framework.TestCase;

public class TestTableSubquery extends TestCase {
    private EPServiceProvider epService;
    private SupportUpdateListener listener;

    public void setUp() {
        epService = EPServiceProviderManager.getDefaultProvider(SupportConfigFactory.getConfiguration());
        epService.initialize();
        for (Class clazz : new Class[] {SupportBean.class, SupportBean_S0.class, SupportBean_S1.class}) {
            epService.getEPAdministrator().getConfiguration().addEventType(clazz);
        }
        listener = new SupportUpdateListener();
        if (InstrumentationHelper.ENABLED) { InstrumentationHelper.startTest(epService, this.getClass(), getName());}
    }

    public void tearDown() {
        if (InstrumentationHelper.ENABLED) { InstrumentationHelper.endTest();}
        listener = null;
    }

    public void testSubquery() throws Exception {
        // subquery against keyed
        epService.getEPAdministrator().createEPL("create table varagg as (" +
                "key string primary key, total sum(int))");
        epService.getEPAdministrator().createEPL("into table varagg " +
                "select sum(intPrimitive) as total from SupportBean group by theString");
        epService.getEPAdministrator().createEPL("select (select total from varagg where key = s0.p00) as value " +
                "from SupportBean_S0 as s0").addListener(listener);

        epService.getEPRuntime().sendEvent(new SupportBean("G2", 200));
        assertValues(epService, listener, "G1,G2", new Integer[] {null, 200});

        epService.getEPRuntime().sendEvent(new SupportBean("G1", 100));
        assertValues(epService, listener, "G1,G2", new Integer[] {100, 200});
        epService.getEPAdministrator().destroyAllStatements();

        // subquery against unkeyed
        epService.getEPAdministrator().createEPL("create table InfraOne (string string, intPrimitive int)");
        epService.getEPAdministrator().createEPL("select (select intPrimitive from InfraOne where string = s0.p00) as c0 from SupportBean_S0 as s0").addListener(listener);
        epService.getEPAdministrator().createEPL("insert into InfraOne select theString as string, intPrimitive from SupportBean");

        epService.getEPRuntime().sendEvent(new SupportBean("E1", 10));
        epService.getEPRuntime().sendEvent(new SupportBean_S0(0, "E1"));
        EPAssertionUtil.assertProps(listener.assertOneGetNewAndReset(), "c0".split(","), new Object[]{10});
    }

    private static void assertValues(EPServiceProvider engine, SupportUpdateListener listener, String keys, Integer[] values) {
        String[] keyarr = keys.split(",");
        for (int i = 0; i < keyarr.length; i++) {
            engine.getEPRuntime().sendEvent(new SupportBean_S0(0, keyarr[i]));
            EventBean event = listener.assertOneGetNewAndReset();
            assertEquals("Failed for key '" + keyarr[i] + "'", values[i], event.get("value"));
        }
    }
}
