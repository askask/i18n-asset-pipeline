/*
 * I18nProcessorSpec.groovy
 *
 * Copyright (c) 2014-2015, Daniel Ellermann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package asset.pipeline.i18n

import asset.pipeline.AssetFile
import asset.pipeline.GenericAssetFile
import org.springframework.core.io.InputStreamResource
import org.springframework.core.io.ResourceLoader
import spock.lang.Specification


class I18nProcessorSpec extends Specification {

    //-- Constants ------------------------------

    protected static final String MESSAGES = '''
# Example message file
foo.foo = Test
foo.bar = Another test

special.empty =
special.backslash = Test \\\\{0\\\\}
special.crlf = This is\\n\\
        a test.
special.quotationMarks = This is a "test".
format.1 = {0}
format.2 = {0}{1}{2}
format.4 = {2} {1} {0}
test.b= 0 {0}
test.c= {0} 0
format.3 = {1}
format.7 = {}
'''


    //-- Instance variables ---------------------

    AssetFile assetFile
    I18nProcessor processor


    //-- Fixture methods ------------------------

    def setup() {

        // first create a i18n processor instance using this message file
        processor = new I18nProcessor(null)
        processor.resourceLoader = Mock(ResourceLoader)
        processor.resourceLoader.getResource(_) >> new InputStreamResource(
            new ByteArrayInputStream(MESSAGES.bytes)
        )

        // then create a mock asset file
        assetFile = new GenericAssetFile(path: '/foo/bar/mymessages.i18n')
    }


    //-- Feature methods ------------------------

    def 'Handle language files correctly'() {
        given: 'an i18n processor'
        def processor = new I18nProcessor(null)

        and: 'a mock that is called with a particular parameter'
        processor.resourceLoader = Mock(ResourceLoader)
        processor.resourceLoader.getResource(
                'grails-app/i18n/messages_de.properties'
            ) >> new InputStreamResource(
                new ByteArrayInputStream(MESSAGES.bytes)
            )

        and: 'a localized mock asset file'
        def assetFile = new GenericAssetFile(
            path: '/foo/bar/mymessages_de.i18n'
        )

        when: 'I process an empty string'
        String res = processor.process('', assetFile)

        then: 'I do not get an error message'
        '' != res
    }

    def 'Process empty i18n file is possible'() {
        when: 'I process an empty i18n file'
        String res = processor.process('', assetFile)

        then:
        getJavaScriptCode('') == res
    }

    def 'Process i18n file with valid message codes is possible'() {
        when: 'I process an i18n file containing valid message codes'
        String res = processor.process('foo.bar\nfoo.foo', assetFile)

        then:
        getJavaScriptCode('''        "foo.bar": "Another test",
        "foo.foo": "Test"''') == res
    }

    def 'Process i18n file with invalid message codes is possible'() {
        when: 'I process an i18n file containing valid message codes'
        String res = processor.process('foo.bar\nfoo.whee', assetFile)

        then:
        getJavaScriptCode('''        "foo.bar": "Another test",
        "foo.whee": "foo.whee"''') == res
    }

    def 'Special characters have been escaped correctly'() {
        when: 'I process an i18n file containing valid message codes'
        String res = processor.process(
            '''special.backslash
special.empty
special.quotationMarks
special.crlf''',
            assetFile
        )

        then:
        getJavaScriptCode(
            '''        "special.backslash": "Test \\\\{0\\\\}",
        "special.empty": "",
        "special.quotationMarks": "This is a \\"test\\".",
        "special.crlf": "This is\\na test."'''
            ) == res
    }

    def 'String subst 1'() {
        when: 'I process an i18n file containing xxxxxxxx'
        String res = processor.process(
            '''format.1
format.2
format.4
test.b
test.c
format.3
format.7''',
            assetFile
        )

        then:
        getJavaScriptCode(
            '''        "format.1": [0],
        "format.2": [0, 1, 2],
        "format.4": [2, " ", 1, " ", 0],
        "test.b": ["0 ", 0],
        "test.c": [0, " 0"],
        "format.3": [1],
        "format.7": "{}"'''
            ) == res
    }
    //-- Non-public methods ---------------------

    String getJavaScriptCode(String messages) {
        StringBuilder buf = new StringBuilder('''(function (win) {
    var messages = {
''')
        buf << messages
        buf << '''
    };

    win.$L = function (code) {
        var message = messages[code];
        if(message === undefined) {
            return "[" + code + "]";
        } else if(message instanceof Array) {
            var params;
            if (arguments.length === 2 && (arguments[1]) instanceof Array) {
                params = arguments[1];
            } else {
                params = Array.prototype.slice.call(arguments);
                params.shift();
            }
            var result = "";
            for(var i = 0; i < message.length; i++) {
                if(typeof message[i] === "number") {
                    result += params[message[i]];
                } else {
                    result += message[i];
                }
            }
            return result;
        } else {
            return message;
        }
    }
}(this));
'''

        buf.toString()
    }
}
