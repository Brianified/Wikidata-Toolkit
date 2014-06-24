package org.wikidata.wdtk.rdf;

/*
 * #%L
 * Wikidata Toolkit RDF
 * %%
 * Copyright (C) 2014 Wikidata Toolkit Developers
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.http.client.utils.URIBuilder;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wikidata.wdtk.datamodel.interfaces.DatatypeIdValue;
import org.wikidata.wdtk.datamodel.interfaces.EntityIdValue;
import org.wikidata.wdtk.datamodel.interfaces.GlobeCoordinatesValue;
import org.wikidata.wdtk.datamodel.interfaces.PropertyIdValue;
import org.wikidata.wdtk.datamodel.interfaces.QuantityValue;
import org.wikidata.wdtk.datamodel.interfaces.StringValue;
import org.wikidata.wdtk.datamodel.interfaces.TimeValue;
import org.wikidata.wdtk.util.WebResourceFetcher;
import org.wikidata.wdtk.util.WebResourceFetcherImpl;

/**
 * This class helps to manage the exact datatype of properties used in an RDF
 * dump. It caches known types and fetches type information from the Web if
 * needed.
 * 
 * @author Markus Kroetzsch
 * 
 */
public class WikidataPropertyTypes implements PropertyTypes {

	static final Logger logger = LoggerFactory.getLogger(WikidataPropertyTypes.class);
	
	final String WEB_API_URL = "http://www.wikidata.org/w/api.php";

	final Map<String, String> propertyTypes;

	PropertyIdValue propertyRegister = null;
	String webAPIUrl;

	WebResourceFetcher webResourceFetcher = new WebResourceFetcherImpl();

	public WikidataPropertyTypes() {
		this.propertyTypes = new HashMap<String, String>();
		this.propertyTypes.putAll(WikidataPropertyTypes.KNOWN_PROPERTY_TYPES);
		this.webAPIUrl = this.WEB_API_URL;
	}

	@Override
	public String getPropertyType(PropertyIdValue propertyIdValue) {
		if (!propertyTypes.containsKey(propertyIdValue.getId())) {
			try {
				propertyTypes.put(propertyIdValue.getId(),
						fetchPropertyType(propertyIdValue));
			} catch (IOException e) {
				logger.error(e.toString());
			} catch (URISyntaxException e) {
				logger.error(e.toString());
			}
		}
		return propertyTypes.get(propertyIdValue.getId());
	}

	@Override
	public void setPropertyType(PropertyIdValue propertyIdValue,
			String datatypeIri) {
		propertyTypes.put(propertyIdValue.getId(), datatypeIri);
	}

	@Override
	public String setPropertyTypeFromEntityIdValue(
			PropertyIdValue propertyIdValue, EntityIdValue value) {
		// Only Items can be used as entity values so far
		return DatatypeIdValue.DT_ITEM;
	}

	@Override
	public String setPropertyTypeFromGlobeCoordinatesValue(
			PropertyIdValue propertyIdValue, GlobeCoordinatesValue value) {
		return DatatypeIdValue.DT_GLOBE_COORDINATES;
	}

	@Override
	public String setPropertyTypeFromQuantityValue(
			PropertyIdValue propertyIdValue, QuantityValue value) {
		return DatatypeIdValue.DT_QUANTITY;
	}

	@Override
	public String setPropertyTypeFromStringValue(
			PropertyIdValue propertyIdValue, StringValue value) {
		String datatype = getPropertyType(propertyIdValue);
		if (datatype == null) {
			return DatatypeIdValue.DT_STRING; // default type for StringValue
		} else {
			return datatype;
		}
	}

	@Override
	public String setPropertyTypeFromTimeValue(PropertyIdValue propertyIdValue,
			TimeValue value) {
		return DatatypeIdValue.DT_TIME;
	}

	/**
	 * Find the datatype of a property online.
	 * 
	 * @param propertyIdValue
	 * @return
	 * @throws IOException
	 * @throws URISyntaxException
	 *             , IOException
	 */
	String fetchPropertyType(PropertyIdValue propertyIdValue)
			throws IOException, URISyntaxException {
		logger.info("Fetching datatype of property " + propertyIdValue.getId()
				+ " online.");

		URIBuilder uriBuilder;
		uriBuilder = new URIBuilder(this.webAPIUrl);
		uriBuilder.setParameter("action", "wbgetentities");
		uriBuilder.setParameter("ids", propertyIdValue.getId());
		uriBuilder.setParameter("format", "json");
		uriBuilder.setParameter("props", "datatype");
		InputStream inStream = this.webResourceFetcher
				.getInputStreamForUrl(uriBuilder.toString());
		JSONObject jsonResult = new JSONObject(IOUtils.toString(inStream));
		String datatype = jsonResult.getJSONObject("entities")
				.getJSONObject(propertyIdValue.getId()).getString("datatype");
		switch (datatype) {
		case "wikibase-item":
			return DatatypeIdValue.DT_ITEM;
		case "string":
			return DatatypeIdValue.DT_STRING;
		case "quantity":
			return DatatypeIdValue.DT_QUANTITY;
		case "url":
			return DatatypeIdValue.DT_URL;
		case "globe-coordinate":
			return DatatypeIdValue.DT_GLOBE_COORDINATES;
		case "time":
			return DatatypeIdValue.DT_TIME;
		case "commonsMedia":
			return DatatypeIdValue.DT_COMMONS_MEDIA;
		default:
			logger.error("Got unkown datatype " + datatype);
			return null;
		}
	}

	void registerProperty(PropertyIdValue propertyIdValue) {
		propertyRegister = propertyIdValue;
	}

	int getIntId(String propertyId) {
		return Integer.parseInt(propertyId.substring(1));
	}

	void quicksort(List<String> list, int low, int high) {
		int i = low;
		int j = high;
		String pivotString = list.get(low + (high - low) / 2);
		int pivot = getIntId(pivotString);
		while (i <= j) {
			while (getIntId(list.get(i)) < pivot) {
				i++;
			}
			while (getIntId(list.get(j)) > pivot) {
				j--;
			}
			if (i <= j) {
				String tmp = list.get(i);
				list.set(i, list.get(j));
				list.set(j, tmp);
				i++;
				j--;
			}
		}
		if ((i >= list.size()) || (j < 0)) {
			return;
		}

		if (low < j) {
			quicksort(list, low, j);
		}
		if (j < high) {
			quicksort(list, i, high);
		}
	}

	List<String> sortByPropertyKey(List<String> keyList) {
		quicksort(keyList, 0, keyList.size() - 1);
		return keyList;
	}

	@Override
	public void getPropertyList(OutputStream out) throws IOException{
		out.write("	static Map<String, String> KNOWN_PROPERTY_TYPES = new HashMap<String, String>();\n	static {\n"
				.getBytes(StandardCharsets.UTF_8));
		List<String> keyList = sortByPropertyKey(new ArrayList<String>(
				propertyTypes.keySet()));
		for (String key : keyList) {
			String datatypeNotation = new String();
			String typeIri = propertyTypes.get(key);
			switch (typeIri) {
			case DatatypeIdValue.DT_COMMONS_MEDIA:
				datatypeNotation = "DT_COMMONS_MEDIA";
				break;
			case DatatypeIdValue.DT_GLOBE_COORDINATES:
				datatypeNotation = "DT_GLOBE_COORDINATES";
				break;
			case DatatypeIdValue.DT_ITEM:
				datatypeNotation = "DT_ITEM";
				break;
			case DatatypeIdValue.DT_QUANTITY:
				datatypeNotation = "DT_QUANTITY";
				break;
			case DatatypeIdValue.DT_STRING:
				datatypeNotation = "DT_STRING";
				break;
			case DatatypeIdValue.DT_TIME:
				datatypeNotation = "DT_TIME";
				break;
			case DatatypeIdValue.DT_URL:
				datatypeNotation = "DT_URL";
				break;
			default:
				logger.warn("unknown IRI " + typeIri);
				datatypeNotation = null;

			}
			if (datatypeNotation != null) {
				out.write(("		KNOWN_PROPERTY_TYPES.put(\"" + key
						+ "\", DatatypeIdValue." + datatypeNotation + ");\n")
						.getBytes(StandardCharsets.UTF_8));
			}
		}
		out.write("}".getBytes(StandardCharsets.UTF_8));
	}

	static Map<String, String> KNOWN_PROPERTY_TYPES = new HashMap<String, String>();
	static {
		KNOWN_PROPERTY_TYPES.put("P10", DatatypeIdValue.DT_COMMONS_MEDIA);
		KNOWN_PROPERTY_TYPES.put("P1001", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P1002", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P1003", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P1004", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P1005", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P1006", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P101", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P1013", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P1014", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P1015", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P1016", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P1017", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P1019", DatatypeIdValue.DT_URL);
		KNOWN_PROPERTY_TYPES.put("P102", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P1025", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P1027", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P103", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P1030", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P1031", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P1033", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P1034", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P1036", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P1037", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P1038", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P1039", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P1040", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P1042", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P1044", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P1047", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P1048", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P105", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P1054", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P1055", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P1056", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P1058", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P1059", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P106", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P1065", DatatypeIdValue.DT_URL);
		KNOWN_PROPERTY_TYPES.put("P1066", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P1067", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P1069", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P107", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P1070", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P1074", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P1075", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P1076", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P1077", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P108", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P1080", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P1081", DatatypeIdValue.DT_QUANTITY);
		KNOWN_PROPERTY_TYPES.put("P1082", DatatypeIdValue.DT_QUANTITY);
		KNOWN_PROPERTY_TYPES.put("P1085", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P1086", DatatypeIdValue.DT_QUANTITY);
		KNOWN_PROPERTY_TYPES.put("P109", DatatypeIdValue.DT_COMMONS_MEDIA);
		KNOWN_PROPERTY_TYPES.put("P1092", DatatypeIdValue.DT_QUANTITY);
		KNOWN_PROPERTY_TYPES.put("P110", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P1100", DatatypeIdValue.DT_QUANTITY);
		KNOWN_PROPERTY_TYPES.put("P1101", DatatypeIdValue.DT_QUANTITY);
		KNOWN_PROPERTY_TYPES.put("P1103", DatatypeIdValue.DT_QUANTITY);
		KNOWN_PROPERTY_TYPES.put("P1104", DatatypeIdValue.DT_QUANTITY);
		KNOWN_PROPERTY_TYPES.put("P1107", DatatypeIdValue.DT_QUANTITY);
		KNOWN_PROPERTY_TYPES.put("P1108", DatatypeIdValue.DT_QUANTITY);
		KNOWN_PROPERTY_TYPES.put("P1110", DatatypeIdValue.DT_QUANTITY);
		KNOWN_PROPERTY_TYPES.put("P1113", DatatypeIdValue.DT_QUANTITY);
		KNOWN_PROPERTY_TYPES.put("P1114", DatatypeIdValue.DT_QUANTITY);
		KNOWN_PROPERTY_TYPES.put("P1115", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P1118", DatatypeIdValue.DT_QUANTITY);
		KNOWN_PROPERTY_TYPES.put("P1119", DatatypeIdValue.DT_QUANTITY);
		KNOWN_PROPERTY_TYPES.put("P112", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P1120", DatatypeIdValue.DT_QUANTITY);
		KNOWN_PROPERTY_TYPES.put("P1121", DatatypeIdValue.DT_QUANTITY);
		KNOWN_PROPERTY_TYPES.put("P1128", DatatypeIdValue.DT_QUANTITY);
		KNOWN_PROPERTY_TYPES.put("P113", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P1130", DatatypeIdValue.DT_QUANTITY);
		KNOWN_PROPERTY_TYPES.put("P1132", DatatypeIdValue.DT_QUANTITY);
		KNOWN_PROPERTY_TYPES.put("P1134", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P114", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P1142", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P1144", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P1146", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P1148", DatatypeIdValue.DT_QUANTITY);
		KNOWN_PROPERTY_TYPES.put("P1149", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P115", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P1150", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P117", DatatypeIdValue.DT_COMMONS_MEDIA);
		KNOWN_PROPERTY_TYPES.put("P118", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P119", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P121", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P122", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P123", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P126", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P127", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P131", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P132", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P133", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P134", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P135", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P136", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P137", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P138", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P14", DatatypeIdValue.DT_COMMONS_MEDIA);
		KNOWN_PROPERTY_TYPES.put("P140", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P141", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P143", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P144", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P149", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P15", DatatypeIdValue.DT_COMMONS_MEDIA);
		KNOWN_PROPERTY_TYPES.put("P150", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P154", DatatypeIdValue.DT_COMMONS_MEDIA);
		KNOWN_PROPERTY_TYPES.put("P155", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P156", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P157", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P158", DatatypeIdValue.DT_COMMONS_MEDIA);
		KNOWN_PROPERTY_TYPES.put("P159", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P16", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P161", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P162", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P163", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P166", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P167", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P168", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P169", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P17", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P170", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P171", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P172", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P173", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P175", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P176", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P177", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P178", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P179", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P18", DatatypeIdValue.DT_COMMONS_MEDIA);
		KNOWN_PROPERTY_TYPES.put("P180", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P181", DatatypeIdValue.DT_COMMONS_MEDIA);
		KNOWN_PROPERTY_TYPES.put("P183", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P184", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P185", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P186", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P189", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P19", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P190", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P193", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P194", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P195", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P196", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P197", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P198", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P199", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P20", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P200", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P201", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P202", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P205", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P206", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P208", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P209", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P21", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P210", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P212", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P213", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P214", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P215", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P217", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P218", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P219", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P22", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P220", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P223", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P225", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P227", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P229", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P230", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P231", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P232", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P233", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P234", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P235", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P236", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P237", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P238", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P239", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P240", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P241", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P242", DatatypeIdValue.DT_COMMONS_MEDIA);
		KNOWN_PROPERTY_TYPES.put("P243", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P244", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P245", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P246", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P247", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P248", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P249", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P25", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P26", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P263", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P264", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P268", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P269", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P27", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P270", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P271", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P272", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P274", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P275", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P276", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P277", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P279", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P281", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P282", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P286", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P287", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P289", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P291", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P295", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P296", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P297", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P298", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P299", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P30", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P300", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P301", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P304", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P306", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P31", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P344", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P345", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P347", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P348", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P349", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P35", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P350", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P352", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P355", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P356", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P357", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P358", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P359", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P36", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P360", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P361", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P364", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P366", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P367", DatatypeIdValue.DT_COMMONS_MEDIA);
		KNOWN_PROPERTY_TYPES.put("P37", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P370", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P371", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P373", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P374", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P375", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P376", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P377", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P38", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P380", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P381", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P382", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P387", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P39", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P392", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P393", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P395", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P396", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P397", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P40", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P400", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P402", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P403", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P404", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P405", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P406", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P407", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P408", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P409", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P41", DatatypeIdValue.DT_COMMONS_MEDIA);
		KNOWN_PROPERTY_TYPES.put("P410", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P412", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P413", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P414", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P417", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P418", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P421", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P424", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P425", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P426", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P427", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P428", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P429", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P43", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P432", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P433", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P434", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P435", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P436", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P437", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P438", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P439", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P44", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P440", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P442", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P443", DatatypeIdValue.DT_COMMONS_MEDIA);
		KNOWN_PROPERTY_TYPES.put("P444", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P447", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P448", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P449", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P45", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P450", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P451", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P452", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P453", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P454", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P455", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P457", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P458", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P459", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P460", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P461", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P462", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P463", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P465", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P466", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P467", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P47", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P473", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P474", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P477", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P478", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P480", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P484", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P485", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P486", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P487", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P488", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P489", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P490", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P492", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P493", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P494", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P495", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P497", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P498", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P50", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P500", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P501", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P504", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P506", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P508", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P509", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P51", DatatypeIdValue.DT_COMMONS_MEDIA);
		KNOWN_PROPERTY_TYPES.put("P511", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P512", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P513", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P516", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P518", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P520", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P521", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P522", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P523", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P524", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P525", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P527", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P528", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P529", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P53", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P530", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P531", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P532", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P535", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P536", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P539", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P54", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P542", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P543", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P545", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P547", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P549", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P551", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P552", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P553", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P554", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P555", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P557", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P558", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P559", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P560", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P561", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P562", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P563", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P564", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P566", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P569", DatatypeIdValue.DT_TIME);
		KNOWN_PROPERTY_TYPES.put("P57", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P570", DatatypeIdValue.DT_TIME);
		KNOWN_PROPERTY_TYPES.put("P571", DatatypeIdValue.DT_TIME);
		KNOWN_PROPERTY_TYPES.put("P574", DatatypeIdValue.DT_TIME);
		KNOWN_PROPERTY_TYPES.put("P575", DatatypeIdValue.DT_TIME);
		KNOWN_PROPERTY_TYPES.put("P576", DatatypeIdValue.DT_TIME);
		KNOWN_PROPERTY_TYPES.put("P577", DatatypeIdValue.DT_TIME);
		KNOWN_PROPERTY_TYPES.put("P579", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P58", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P580", DatatypeIdValue.DT_TIME);
		KNOWN_PROPERTY_TYPES.put("P582", DatatypeIdValue.DT_TIME);
		KNOWN_PROPERTY_TYPES.put("P585", DatatypeIdValue.DT_TIME);
		KNOWN_PROPERTY_TYPES.put("P586", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P587", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P59", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P590", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P592", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P597", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P599", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P6", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P60", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P600", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P604", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P605", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P606", DatatypeIdValue.DT_TIME);
		KNOWN_PROPERTY_TYPES.put("P607", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P608", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P609", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P61", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P610", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P611", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P612", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P613", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P618", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P619", DatatypeIdValue.DT_TIME);
		KNOWN_PROPERTY_TYPES.put("P624", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P625", DatatypeIdValue.DT_GLOBE_COORDINATES);
		KNOWN_PROPERTY_TYPES.put("P627", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P629", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P630", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P631", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P633", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P634", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P635", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P637", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P638", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P640", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P641", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P642", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P646", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P648", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P649", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P65", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P653", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P655", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P657", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P658", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P66", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P661", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P662", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P664", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P665", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P668", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P669", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P670", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P672", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P673", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P674", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P676", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P677", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P680", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P681", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P682", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P683", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P685", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P686", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P687", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P69", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P691", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P694", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P695", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P697", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P7", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P70", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P702", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P703", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P705", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P706", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P708", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P709", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P71", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P710", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P711", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P712", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P713", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P714", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P715", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P716", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P718", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P720", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P721", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P722", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P725", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P729", DatatypeIdValue.DT_TIME);
		KNOWN_PROPERTY_TYPES.put("P734", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P735", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P736", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P737", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P74", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P740", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P741", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P742", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P743", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P744", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P747", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P749", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P750", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P757", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P758", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P759", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P76", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P761", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P762", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P763", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P764", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P765", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P766", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P767", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P768", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P77", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P770", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P771", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P772", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P773", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P774", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P775", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P78", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P780", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P782", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P790", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P791", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P792", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P793", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P794", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P799", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P800", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P802", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P803", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P804", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P805", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P806", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P808", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P809", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P81", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P813", DatatypeIdValue.DT_TIME);
		KNOWN_PROPERTY_TYPES.put("P814", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P815", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P816", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P817", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P827", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P828", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P829", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P830", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P831", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P832", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P833", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P836", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P837", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P838", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P84", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P840", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P841", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P842", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P846", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P849", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P85", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P850", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P853", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P854", DatatypeIdValue.DT_URL);
		KNOWN_PROPERTY_TYPES.put("P856", DatatypeIdValue.DT_URL);
		KNOWN_PROPERTY_TYPES.put("P858", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P86", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P862", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P865", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P866", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P867", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P868", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P87", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P872", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P878", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P879", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P88", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P882", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P883", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P884", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P888", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P898", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P9", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P901", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P902", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P905", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P906", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P908", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P909", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P91", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P910", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P912", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P913", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P914", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P915", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P916", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P92", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P921", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P931", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P933", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P935", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P937", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P94", DatatypeIdValue.DT_COMMONS_MEDIA);
		KNOWN_PROPERTY_TYPES.put("P941", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P944", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P945", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P946", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P947", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P948", DatatypeIdValue.DT_COMMONS_MEDIA);
		KNOWN_PROPERTY_TYPES.put("P949", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P950", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P951", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P954", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P957", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P958", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P959", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P960", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P961", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P963", DatatypeIdValue.DT_URL);
		KNOWN_PROPERTY_TYPES.put("P964", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P965", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P966", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P969", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P97", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P971", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P972", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P973", DatatypeIdValue.DT_URL);
		KNOWN_PROPERTY_TYPES.put("P98", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P982", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P984", DatatypeIdValue.DT_STRING);
		KNOWN_PROPERTY_TYPES.put("P990", DatatypeIdValue.DT_COMMONS_MEDIA);
		KNOWN_PROPERTY_TYPES.put("P991", DatatypeIdValue.DT_ITEM);
		KNOWN_PROPERTY_TYPES.put("P996", DatatypeIdValue.DT_COMMONS_MEDIA);
		KNOWN_PROPERTY_TYPES.put("P998", DatatypeIdValue.DT_STRING);
	}

}
