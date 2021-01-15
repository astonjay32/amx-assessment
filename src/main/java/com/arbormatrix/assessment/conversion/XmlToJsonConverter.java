package com.arbormatrix.assessment.conversion;

import com.google.common.io.Resources;
import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Converts XML into JSON, translating any field names, values and types per the defined mapping file
 */
public class XmlToJsonConverter {

    // Constants for mapping file fields.  Ideally, these would all be in a separate class,
    // but the requirements say to use a single class, so this will have to do.
    private static final String MAPPING_ROOT = "root";
    private static final String MAPPING_ROOT_STRUCTURE = "structure";
    private static final String MAPPING_ROOT_STRUCTURE_ARRAY = "array";
    private static final String MAPPING_ROOT_TAG = "tagName";
    private static final String MAPPING_ROOT_COLLECTION_TYPE = "collectionType";
    private static final String MAPPING_OBJECTS = "objects";
    private static final String MAPPING_OBJECTS_FIELDS = "fields";
    private static final String MAPPING_OBJECTS_SOURCE_NAME = "sourceName";
    private static final String MAPPING_OBJECTS_TARGET_NAME = "targetName";
    private static final String MAPPING_OBJECTS_TARGET_TYPE = "targetType";
    private static final String MAPPING_OBJECTS_TYPE_INTEGER = "java.lang.Integer";
    private static final String MAPPING_OBJECTS_TRANSLATION_METHOD = "translationMethod";
    private static final String DATE_FORMAT = "dd/MM/yyyy"; // TODO: Consider pulling this from mapping file

    private static final Logger LOGGER = Logger.getLogger(XmlToJsonConverter.class);

    /**
     * Converts a locally sourced XML input file into a JSON file using mapping rules defined in the mapping file.
     * Both files are currently assumed to be in the classpath, and as such, some work would have to be done to
     * ensure a file path from outside the JAR would work.
     *
     * Current output is simply a string, but obviously we could extend this to return a File or even a JSONObject.
     * @param inputFile Path to the input XML file, assumed to be in resources folder for now
     * @param mappingFile Path to the mappingFile, JSON formatted.
     * @return JSON representation of the XML with mapping translations applied
     */
    public String toString(String inputFile, String mappingFile){
        try {

            // TODO: In prod, consider SAX, which is better performing
            Document doc = loadXml(inputFile);

            // Load mapping file to JSON object, determine our root-level tag and structure
            JSONParser jsonParser = new JSONParser();
            JSONObject mappingJson = (JSONObject) jsonParser.parse(stringFromFile(mappingFile));
            JSONObject mappingRoot = (JSONObject) mappingJson.get(MAPPING_ROOT);
            String mappingRootStructure = (String) mappingRoot.get(MAPPING_ROOT_STRUCTURE);
            String mappingRootTag = (String) mappingRoot.get(MAPPING_ROOT_TAG);
            StringWriter out = new StringWriter();

            if(MAPPING_ROOT_STRUCTURE_ARRAY.equals(mappingRootStructure)){
                String mappingRootCollectionType = (String) mappingRoot.get(MAPPING_ROOT_COLLECTION_TYPE);
                NodeList nList = doc.getElementsByTagName(mappingRootCollectionType);
                JSONArray outputArray = buildJsonArray(nList, mappingRootCollectionType, mappingJson);
                outputArray.writeJSONString(out);
                return out.toString();
            }else{
                Node rootNode = doc.getDocumentElement();
                JSONObject outputObject = buildJsonObject(rootNode, mappingRootTag, mappingJson);
                outputObject.writeJSONString(out);
                return out.toString();
            }

        }
        // Look ... I know these catch-all Exceptions are bad practice. I usually reject a code review when I see them.
        // However, based on the time constraints of this exercise, and the lack of requirements for what to do in certain
        // error scenarios, I decided to simply employ the 'One Exception To Rule Them All approach'. Please don't judge me!
        catch (Exception e) {
            LOGGER.error("Something went wrong!", e);
        }
        return null;
    }

    /**
     * Returns a fully parsed XML object into a Document object. Will not perform well with large files.
     * Consider using SAX for a production environment.
     * @param inputFile Path (assumed local) to the XML file
     * @return Document tree of the XML in Java object form
     * @throws URISyntaxException
     * @throws ParserConfigurationException
     * @throws IOException
     * @throws SAXException
     */
    private Document loadXml(String inputFile) throws URISyntaxException, ParserConfigurationException, IOException, SAXException {
        File fXmlFile = fileFromResource(inputFile);
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(fXmlFile);
        doc.getDocumentElement().normalize();
        return doc;
    }

    /**
     * Returns a File from a string on the class path. Will NOT work if in a JAR!
     * @param fileName Path to a file on the classpath, or resource directory
     * @return File
     * @throws URISyntaxException
     */
    private File fileFromResource(String fileName) throws URISyntaxException {
        ClassLoader classLoader = getClass().getClassLoader();
        URL resource = classLoader.getResource(fileName);
        if (resource == null) {
            throw new IllegalArgumentException("file not found! " + fileName);
        } else {
            return new File(resource.toURI());
        }
    }

    /**
     * Returns the String contents of a File on the classpath
     * @param resourceFile
     * @return
     * @throws IOException
     */
    private String stringFromFile(String resourceFile) throws IOException {
        URL url = Resources.getResource(resourceFile);
        return Resources.toString(url, UTF_8);
    }

    /**
     * Traverses a NodeList object, performs any necessary translations using the mapping file,
     * and returns the JSONArray representation
     * @param nList XML based node list
     * @param collectionType The type of objects contained in the list
     * @param mappingJson The mapping rules for transforming data
     * @return JSONArray will all necessary translations
     * @throws IllegalAccessException
     * @throws InstantiationException
     * @throws ClassNotFoundException
     * @throws NoSuchMethodException
     * @throws InvocationTargetException
     */
    private JSONArray buildJsonArray(NodeList nList, String collectionType, JSONObject mappingJson) throws IllegalAccessException, InstantiationException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException {
        JSONArray outputArray = new JSONArray();
        for (int temp = 0; temp < nList.getLength(); temp++) {
            Node nNode = nList.item(temp);
            if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                outputArray.add(buildJsonObject(nNode, collectionType, mappingJson));
            }
        }
        return outputArray;
    }

    /**
     * Returns a JSONObject representation of an XML Node, performing any transformations per the mapping instrucitons
     * @param node XML node
     * @param type Object type to convert to
     * @param mappingJson Instructions for transformations
     * @return
     * @throws ClassNotFoundException
     * @throws IllegalAccessException
     * @throws InstantiationException
     * @throws NoSuchMethodException
     * @throws InvocationTargetException
     */
    private JSONObject buildJsonObject(Node node, String type, JSONObject mappingJson) throws ClassNotFoundException, IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException {

        Element eElement = (Element) node;
        JSONObject outputObject = new JSONObject();
        JSONObject mappingType = getMappingType(type, mappingJson);
        JSONArray mappingFields = (JSONArray) mappingType.get(MAPPING_OBJECTS_FIELDS);

        // Loop over each field, perfofrm any translations if needed
        for(Object mappingFieldObj : mappingFields){
            JSONObject mappingField = (JSONObject) mappingFieldObj;
            String mappingSourceName = (String) mappingField.get(MAPPING_OBJECTS_SOURCE_NAME);
            String xmlValue = eElement.getElementsByTagName(mappingSourceName).item(0).getTextContent();
            Object targetValue = getTranslatedTargetValue(xmlValue, mappingField);
            String mappingTargetName = (String) mappingField.get(MAPPING_OBJECTS_TARGET_NAME);
            outputObject.put(mappingTargetName, targetValue);
        }

        return outputObject;
    }

    /**
     * Traverses the mapping JSON and returns a JSONObject representing the mapping rules for a given type
     * @param type Name of the type
     * @param mappingJSON
     * @return Mapping rules for the target type
     */
    private JSONObject getMappingType(String type, JSONObject mappingJSON){
        JSONObject mappingObjects = (JSONObject) mappingJSON.get(MAPPING_OBJECTS);
        return (JSONObject) mappingObjects.get(type);
    }

    /**
     * If necessary, performs any custom data translations or type conversions on the given value.
     * Default value will be the original XML string value
     * @param xmlValue The original value in the XML
     * @param mappingField Mapping details for the field defined by the mapping file
     * @return
     * @throws NoSuchMethodException
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     */
    private Object getTranslatedTargetValue(String xmlValue, JSONObject mappingField) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        String mappingTargetType = (String) mappingField.get(MAPPING_OBJECTS_TARGET_TYPE);

        // Do we have to translate the value with a custom static method?
        if(null != mappingField.get(MAPPING_OBJECTS_TRANSLATION_METHOD)){
            Method translationMethod = XmlToJsonConverter.class.getDeclaredMethod((String)mappingField.get(MAPPING_OBJECTS_TRANSLATION_METHOD), String.class);
            return translationMethod.invoke(null, xmlValue);
        }

        // No fancy translation needed, but we may want to convert to a different type
        // Consider some form of reflection to dynamically cast, this won't scale well or support custom classes.
        else if(MAPPING_OBJECTS_TYPE_INTEGER.equals(mappingTargetType)){
            return Integer.valueOf(xmlValue);
        }
        return xmlValue;
    }


    /**
     * Static utility methods for generic transformation.
     * TODO: Consolidate all these generic static methods into a separate class, let mapping file drive class and method
     */
    public static Integer dateToAge(String dateString) throws ParseException {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(DATE_FORMAT);
        Date inputDate = simpleDateFormat.parse(dateString);
        LocalDate birthDate = inputDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate currentDate = LocalDate.now();
        return Period.between(birthDate, currentDate).getYears();
    }

    public static String charToGenderWord(String genderChar){
        if("m".equalsIgnoreCase(genderChar)){
            return "male";
        }else{
            return "female";
        }
    }

    public static String fullStateToAbbreviation(String fullState){
        // TODO: Populate rest of states, or better yet, load from separate class/file/config resource
        Map<String, String> stateMap = new HashMap();
        stateMap.put("michigan", "MI");
        stateMap.put("ohio", "OH");
        return stateMap.get(fullState.toLowerCase());
    }
}
