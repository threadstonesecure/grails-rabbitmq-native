/*
 * Copyright 2013-2017 Bud Byrd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.budjb.rabbitmq.test.support

class MultipleHandlersConsumer {
    static rabbitConfig = [
        queue    : 'foobar',
        consumers: 10
    ]

    enum Handler {
        INTEGER,
        BYTE,
        MAP
    }

    Handler handler

    void handleMessage(Map input) {
        handler = Handler.MAP
    }

    void handleMessage(int input) {
        handler = Handler.INTEGER
    }

    void handleMessage(byte[] input) {
        handler = Handler.BYTE
    }
}
