/*
* Copyright 2013 JBoss Inc
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.switchyard.quickstarts.demo.multiapp.service;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.switchyard.quickstarts.demo.multiapp.Order;
import org.switchyard.quickstarts.demo.multiapp.OrderAck;
import org.switchyard.test.Invoker;
import org.switchyard.test.ServiceOperation;
import org.switchyard.test.SwitchYardRunner;
import org.switchyard.test.SwitchYardTestCaseConfig;
import org.switchyard.component.test.mixins.cdi.CDIMixIn;

@RunWith(SwitchYardRunner.class)
@SwitchYardTestCaseConfig(mixins = CDIMixIn.class)
public class OrderServiceTest {

    @ServiceOperation("OrderService.submitOrder")
    private Invoker submitOrder;

    @Test
    public void testOrderAccepted() throws Exception {
        Order testOrder = new Order()
            .setOrderId("ORDER01")
            .setItemId("BUTTER")
            .setQuantity(100);
        
        OrderAck testAck = submitOrder
            .sendInOut(testOrder)
            .getContent(OrderAck.class);

        Assert.assertTrue(testAck.isAccepted());
    }
}
