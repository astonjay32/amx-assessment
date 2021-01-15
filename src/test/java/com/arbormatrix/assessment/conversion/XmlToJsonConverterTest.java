package com.arbormatrix.assessment.conversion;
import junit.framework.Assert;
import org.junit.Test;
public class XmlToJsonConverterTest {

    @Test
    public void testToString(){

        XmlToJsonConverter subject = new XmlToJsonConverter();

        String actualOutput = subject.toString("patientInput.xml", "patientMapping.json");
        String expectedOutput = "[{\"patientId\":1234,\"sex\":\"male\",\"state\":\"MI\",\"age\":58},{\"patientId\":5678,\"sex\":\"female\",\"state\":\"OH\",\"age\":48}]";

        Assert.assertEquals(actualOutput, expectedOutput);
    }

}
