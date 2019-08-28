/**
 * 
 */
package com.infa.eic.sample;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;

import com.infa.products.ldm.core.rest.v2.client.invoker.ApiException;
import com.infa.products.ldm.core.rest.v2.client.invoker.ApiResponse;
import com.infa.products.ldm.core.rest.v2.client.models.EmbeddedFact;
import com.infa.products.ldm.core.rest.v2.client.models.FactResponse;
import com.infa.products.ldm.core.rest.v2.client.models.Link;
import com.infa.products.ldm.core.rest.v2.client.models.LinkedObjectRequest;
import com.infa.products.ldm.core.rest.v2.client.models.Links;
import com.infa.products.ldm.core.rest.v2.client.models.ObjectIdRequest;
import com.infa.products.ldm.core.rest.v2.client.models.ObjectRefRequest;
import com.infa.products.ldm.core.rest.v2.client.models.ObjectResponse;
import com.infa.products.ldm.core.rest.v2.client.models.ObjectsResponse;
import com.infa.products.ldm.core.rest.v2.client.utils.ObjectAdapter;

import me.xdrop.fuzzywuzzy.FuzzySearch;
import me.xdrop.fuzzywuzzy.model.ExtractedResult;

import com.opencsv.CSVWriter;


/**
 * @author dwrigley
 *
 */
public class ModelLinker {
    public static final String version="1.1";

	String url="";
	String user="";
	String pwd="";
	String entityQuery="";
	String tableQuery="";
	String physNameAttr="";
	String entityAttrLink="";
	String tableToColLink="";
	String logFile="";
	String lineageFile="";
	
	List<String> entityAttrLinks = new ArrayList<String>();
	List<String> physNameAttrs = new ArrayList<String>();
	PrintWriter logWriter;
	PrintWriter lineageWriter;
	
	boolean deleteLinks = false;
	boolean testOnly=true;
	List<String> replaceChars=new ArrayList<String>();


	
	
	private static int totalLinks = 0;
	private static int existingLinks = 0;
	private static int deletedLinks = 0;

	/** 
	 * no-arg constructor
	 */
	public ModelLinker() {
	};


	/**
	 * @param args
	 */
	public static void main(String[] args) {
//		ModelLinker fbg=new ModelLinker();
		
		System.out.println("main here...");
		
		// default property file - catalog_utils.properities (in current folder)
		String propertyFile = "catalog_utils.properties";
		
		if (args.length==0) {
			// assume default property file	(initial value of propertyFile)
		} else {
			propertyFile = args[0];
		}
		
		ModelLinker mdl = new ModelLinker(propertyFile);
		mdl.run();
		mdl.finish();
		

	}
	
	
	/**
	 * constructor - initialize using the property file passed
	 * 
	 * @param propertyFile
	 */
	public ModelLinker(String propertyFile) {
//		System.out.println("Constructor:" + propertyFile);
        System.out.println(this.getClass().getSimpleName() + " " + version +  " initializing properties from: " + propertyFile);
				
		// read the settings needed to control the process from the property file passed as arg[0]
		try {
			System.out.println("reading properties from: " + propertyFile);
			File file = new File(propertyFile);
			FileInputStream fileInput = new FileInputStream(file);
			Properties prop = new Properties();
			prop.load(fileInput);
			fileInput.close();
			
			url = prop.getProperty("rest_service");
			user = prop.getProperty("user");
			pwd = prop.getProperty("password");
			if (pwd.equals("<prompt>") || pwd.isEmpty() ) {
				System.out.println("password set to <prompt> - waiting for user input...");
				pwd = APIUtils.getPassword();
//				System.out.println("pwd entered (debug): " + pwd);
			}
			
			// model linker properties

			entityQuery=prop.getProperty("modelLinker.entityQuery");
			tableQuery=prop.getProperty("modelLinker.tableQuery");
			physNameAttr=prop.getProperty("modelLinker.physicalNameAttr");
			physNameAttrs = new ArrayList<String>(Arrays.asList(physNameAttr.split(",")));
			entityAttrLink=prop.getProperty("modelLinker.entityToAttrLink");
			entityAttrLinks=new ArrayList<String>(Arrays.asList(entityAttrLink.split(",")));
			tableToColLink = prop.getProperty("modelLinker.tableToColLink");
			deleteLinks = Boolean.parseBoolean(prop.getProperty("modelLinker.deleteLinks"));
			logFile = prop.getProperty("modelLinker.logfile", "modellog.log");
			lineageFile = prop.getProperty("modelLinker.lineageFile", "model_lineagee.csv");
			
//			termType = prop.getProperty("fuzzyLink.termType");
			
//			BG_RESOURCE_NAME=prop.getProperty("fuzzyLink.bgResourceName");
			//logFile=prop.getProperty("fuzzyLink.logfile");
//			linkResultFile=prop.getProperty("fuzzyLink.linkResults");
//			searchThreshold=Integer.parseInt(prop.getProperty("fuzzyLink.searchThreshold"));
			testOnly=Boolean.parseBoolean(prop.getProperty("modelLinker.testOnly"));
//			String repChars = prop.getProperty("fuzzyLink.replaceWithSpace", "");
//			if (! repChars.equalsIgnoreCase("")) {
				// only if there are characters to replace
//				replaceChars = new ArrayList<String>(Arrays.asList(repChars.split(",")));
//			}
			
						
	     } catch(Exception e) {
	     	System.out.println("error reading properties file: " + propertyFile);
	     	e.printStackTrace();
	     }
		
		System.out.println("   EDC rest url: " + url);
		System.out.println("    entityQuery: " + entityQuery);
		System.out.println("     tableQuery: " + tableQuery);
		System.out.println("   physNameAttr: " + physNameAttr);
		System.out.println("  physNameAttrs: " + physNameAttrs);
		System.out.println(" entityAttrLink: " + entityAttrLink);
		System.out.println("entityAttrLinks: " + entityAttrLinks);
		System.out.println(" tableToColLink: " + tableToColLink);
		System.out.println("   delete links: " + deleteLinks);
		System.out.println("       log file: " + logFile);
		System.out.println("      test mode: " + testOnly);
//		boolean testOnly=false;

		try {

			System.out.println("\tinitializing logFile:" + logFile);
			
			logWriter = new PrintWriter(logFile, "UTF-8");
			logWriter.println("   EDC rest url: " + url);
			logWriter.println("    entityQuery: " + entityQuery);
			logWriter.println("     tableQuery: " + tableQuery);
			logWriter.println("   physNameAttr: " + physNameAttr);
			logWriter.println("  physNameAttrs: " + physNameAttrs);
			logWriter.println(" entityAttrLink: " + entityAttrLink);
			logWriter.println("entityAttrLinks: " + entityAttrLinks);
			logWriter.println(" tableToColLink: " + tableToColLink);
			logWriter.println("   delete links: " + deleteLinks);
			logWriter.println("       log file: " + logFile);
			logWriter.println("      test mode: " + testOnly);
			
			logWriter.flush();
			
			System.out.println("\tinitializing lineageFile:" + lineageFile);
			lineageWriter = new PrintWriter(lineageFile, "UTF-8");
			lineageWriter.println("Association,From Connection,To Connection,From Object,To Object");
			lineageWriter.flush();

			
//			linkResultCSV = new CSVWriter(new FileWriter(linkResultFile), CSVWriter.DEFAULT_SEPARATOR, CSVWriter.NO_QUOTE_CHARACTER, CSVWriter.NO_ESCAPE_CHARACTER, CSVWriter.DEFAULT_LINE_END);			
//			linkResultCSV.writeNext(new String[] {"fromObjectId","linkedTerm","TermName"});
//			linkResultCSV.flush();

		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		
	}
	
	/**
	 * run the model linker processes
	 */
	private void run() {
//		System.out.println("modelLinker::run()");
		logWriter.println("\nModelLinker Starting");
		
		int errorsFound=0;
		
		//Connect to the EIC REST Instance 
		try {
			APIUtils.setupOnce(url, user, pwd);
			
			int total=1;
			int offset=0;
			final int pageSize=20;

			System.out.println("entity objects.. using query= " + entityQuery);
			logWriter.println("entity objects.. using query= " + entityQuery);
			
			String entityName="";
			String entityId="";
			String physicalName="";
			
			while (offset<total) {
				// EDC (client.jar) <=10.2.1
				//ObjectsResponse response=APIUtils.READER.catalogDataObjectsGet(entityQuery, null, BigDecimal.valueOf(offset), BigDecimal.valueOf(pageSize), false);
				// EDC (client.jar) 10.2.2 (+ 10.2.2 sp1)
				ObjectsResponse response=APIUtils.READER.catalogDataObjectsGet(entityQuery, null, offset, pageSize, null, null);
				// EDC (client.jar) 10.2.2hf1+
//				ObjectsResponse response=APIUtils..catalogDataObjectsGet(entityQuery, null, offset, pageSize, null, null);
				
				total=response.getMetadata().getTotalCount().intValue();
				offset+=pageSize;
				System.out.println("Entities found: " + total);
				logWriter.println("Entities found: " + total);
				
				// process each object returned in this chunk
				for(ObjectResponse or: response.getItems()) {
//					System.out.println("Entity: " + or.getId());
					entityId = or.getId();
					entityName = APIUtils.getValue(or, "core.name");
					physicalName = getPhysicalNameAttr(or, physNameAttrs);
					if (physicalName == null) {
						// use the object name
						// this happens when it is a physical only model (the entity/table name is the name)
						System.out.println("\twarning: no physical name found, using object name = " + entityName);
						logWriter.println("\twarning: no physical name found, using object name = " + entityName);
						physicalName = entityName;
					}
//					physicalName = APIUtils.getValue(or, physNameAttr);
					System.out.println("Entity: " + or.getId() + " name=" + entityName + " physicalName=" + physicalName);
					logWriter.println("Entity: " + or.getId() + " name=" + entityName + " physicalName=" + physicalName);
					
					// find the corresponding table


					// assume only a small set of tables could be returned
					String findTables = tableQuery + " AND core.name_lc_exact:\"" + physicalName + "\"";
					System.out.println("\tfinding table (exact name match): " + findTables);
					logWriter.println("\tfinding table (exact name match): " + findTables);
					// EDC (client.jar) <=10.2.1
					// ObjectsResponse tabsResp=APIUtils.READER.catalogDataObjectsGet(findTables, null, BigDecimal.valueOf(0), BigDecimal.valueOf(10), false);
					// EDC (client.jar) 10.2.2 (+ 10.2.2 sp1)
					ObjectsResponse tabsResp=APIUtils.READER.catalogDataObjectsGet(findTables, null, 0, 10, null, null);
					int tabTotal=tabsResp.getMetadata().getTotalCount().intValue();
					System.out.println("\tTables found=" + tabTotal);
					logWriter.println("\tTables found=" + tabTotal);
					
					if (tabTotal>1) {
						System.out.println("\t> 1 table found - your query probably needs to filter by resourceName or resourceType.  first found will be used here");
						logWriter.println("\t> 1 table found - your query probably needs to filter by resourceName or resourceType.  first found will be used here");
					}
					
					if (tabTotal>=1) {
						// get the first table
						ObjectResponse physTabResp = tabsResp.getItems().get(0);
						System.out.println("\tphysical table id: " + physTabResp.getId());
						
						System.out.println("\t\tlinking objects.... " + entityId + " -> " + physTabResp.getId());
						logWriter.println("\t\tlinking objects.... " + entityId + " -> " + physTabResp.getId());
						addDatasetLink(entityId, physTabResp.getId(), "core.DataSetDataFlow", deleteLinks);
						lineageWriter.println("core.DataSetDataFlow" + ",,," +entityId +"," + physTabResp.getId());
						lineageWriter.flush();

//						hack.addDatasetLink("<from table id>", "<to table id>", "core.DataSetDataFlow");
//						hack.addDatasetLink("<from colmn id>", "<to column id>", "core.DirectionalDataFlow");

						
						// get the columns - for the entity and the table - then match them...
						System.out.println("\tget attrs for entity: using link attribute: " + entityAttrLink);
						logWriter.println("\tget attrs for entity: using link attribute: " + entityAttrLink);
						
						ArrayList<String> attrs = new ArrayList<>();
						attrs.addAll(physNameAttrs);
						attrs.add("core.name");
//						System.out.println("attrs to read=" + attrs);
						
						
						// TODO:  this fails with 10.2.2hf1 (need to test sp1) and hf1 client.jar
						Links entLinks = APIUtils.READER.catalogDataRelationshipsGet(
								new ArrayList<>(Arrays.asList(entityId)), 
								new ArrayList<>(Arrays.asList(entityAttrLink)), 
								BigDecimal.ZERO, "OUT", 
								true, false, 
								attrs
								);
//						System.out.println("links=" + entLinks);
						Map<String,String> entityAttrs = new HashMap<String,String>();
						for (Link entAttrLink: entLinks.getItems()) {
							String attrName = getValueFromEmbeddedFact("core.name", entAttrLink.getInEmbedded().getFacts());;
							String attrPhysName="";
							for (String nameAttr : physNameAttrs) {
								attrPhysName = getValueFromEmbeddedFact(nameAttr, entAttrLink.getInEmbedded().getFacts());
								if (attrPhysName != null) {
									break;
								}
							}

//							String attrPhysName = getValueFromEmbeddedFact(physNameAttr, entAttrLink.getInEmbedded().getFacts());
							System.out.println("\t\tAttribute: " + entAttrLink.getInId() + " attr Name=" + attrName + " physicalName=" + attrPhysName);
							logWriter.println("\t\tAttribute: " + entAttrLink.getInId() + " attr Name=" + attrName + " physicalName=" + attrPhysName);
							
							entityAttrs.put(attrPhysName, entAttrLink.getInId());
						}
						System.out.println("\tattrMapKeys=" + entityAttrs.keySet() );
						logWriter.println("\tattrMapKeys=" + entityAttrs.keySet() );
						// all entit attrs are in the entityAttrs HashMap
						
						// now get the table columns
						Links tabLinks = APIUtils.READER.catalogDataRelationshipsGet(
								new ArrayList<>(Arrays.asList(physTabResp.getId())), 
								new ArrayList<>(Arrays.asList(tableToColLink)), 
								BigDecimal.ZERO, "OUT", 
								true, false, 
								new ArrayList<>(Arrays.asList("core.name"))
								);
//						System.out.println("table columns..." + tabLinks);
						for (Link tabColLink: tabLinks.getItems()) {
							String colName = getValueFromEmbeddedFact("core.name", tabColLink.getInEmbedded().getFacts());;
							System.out.println("\t\tColumn: " + tabColLink.getInId() + " attr Name=" + colName + " match?" + entityAttrs.containsKey(colName));
							logWriter.println("\t\tColumn: " + tabColLink.getInId() + " attr Name=" + colName + " match?" + entityAttrs.containsKey(colName));
							
							if (entityAttrs.containsKey(colName)) {
								String fromId = entityAttrs.get(colName);
								logWriter.println("\t\t\tlinking objects... " + fromId + " -->> " + tabColLink.getInId());
								addDatasetLink(fromId, tabColLink.getInId(), "core.DirectionalDataFlow", deleteLinks);
								lineageWriter.println("core.DirectionalDataFlow" + ",,," +fromId +"," + tabColLink.getInId());
								lineageWriter.flush();

							} else {
								logWriter.println("\tError: cannot find matching column for " + colName);
								errorsFound++;
							}
//							entityAttrs.put(attrPhysName, entAttrLink.getInId());
						}
						
						
					}  // table 1 table was found
					
					System.out.println("\tfinished with: " + entityId);
					logWriter.println("\tfinished with: " + entityId);
					logWriter.flush();
				} // for each object

			} // endwhile (catalog query)


			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
//		try {
			System.out.println("finished... links written=" + totalLinks + " links skipped(existing)=" + existingLinks + " linksDeleted=" + deletedLinks);
			System.out.println("errors:  " + errorsFound);
//			Path toPath = Paths.get(logFile);
//			Charset charset = Charset.forName("UTF-8");
//			Files.write(toPath, logList, charset);
			
			// write the linked objects too
//			toPath = Paths.get(linkResultFile);
//			Charset charset = Charset.forName("UTF-8");
//			Files.write(toPath, linkedObj, charset);
			
//			fuzzySearchCSV.close();
//			linkResultCSV.close();
			
//		} catch (IOException e) {
//			e.printStackTrace();
//		}


		
	}

	private void finish() {
		
//		try {
			logWriter.close();
//		} catch (IOException e1) {
			// TODO Auto-generated catch block
//			e1.printStackTrace();
//		}

	}
	
	private static String getPhysicalNameAttr(ObjectResponse or, List<String> attrNames) {
		String theName = "";
		for (String nameAttr : attrNames) {
//			System.out.println("checking attr=" + nameAttr);
			theName = APIUtils.getValue(or, nameAttr);
//			System.out.println("name=" + theName);
			if (theName != null) {
				break;
			}
		}
//		System.out.println("returning: " + theName);
		return theName;
	}

	private static String getValueFromEmbeddedFact(String physNameAttr, ArrayList<EmbeddedFact> theFacts) {
		String attrName="";
		for(EmbeddedFact aFact:theFacts) {
			if(physNameAttr.equals(aFact.getAttributeId())) {
				attrName = aFact.getValue();
//									System.out.println("name=" + aFact.getValue());
			}
		}
		return attrName;
	}
	
	/**
	 * Utility method to get hash key from value. Works best with unique values.
	 * @param map
	 * @param value
	 * @return
	 */
	public static <T, E> T getKeyByValue(Map<T, E> map, E value) {
	    for (Entry<T, E> entry : map.entrySet()) {
	        if (Objects.equals(value, entry.getValue())) {
	            return entry.getKey();
	        }
	    }
	    return null;
	}
	

	
	/**
	 * Add a TABLE lineage link between the given two objects
	 * @param sourceDatasetObjectID
	 * @param targetDatasetObjectID
	 * @throws Exception
	 */
	public void addDatasetLink(String sourceDatasetObjectID,String targetDatasetObjectID, String linkType, boolean removeLink) throws Exception {
//		System.out.println("link objects:  from: " + sourceDatasetObjectID + " to:" + targetDatasetObjectID + " link:" + linkType + " removeLink=" + removeLink);
		
		if (!testOnly) {
			// get the to object - by id...
			ApiResponse<ObjectResponse> apiResponse=null;
			ApiResponse<ObjectResponse> fromObjectResp=null;
			ObjectIdRequest request=null;
			LinkedObjectRequest link=null;
			boolean isLinkedAlready=false;
			try {
	//			System.out.println("\tvalidating target object: " + targetDatasetObjectID);
				apiResponse=APIUtils.READER.catalogDataObjectsIdGetWithHttpInfo(targetDatasetObjectID);
				request=ObjectAdapter.INSTANCE.copyIntoObjectIdRequest(apiResponse.getData());
				
				// check for existing links
				ArrayList<LinkedObjectRequest> srcObjects=request.getSrcLinks();
	//			System.out.println("\tlinks from to target object already existing..." + srcObjects.size());
				int index=0;
				int remIndex=0;
	//			boolean isLinkedAlready=false;
				for(LinkedObjectRequest or: srcObjects) {
	//				System.out.println("\tlinked object id=" + or.getId() + "linktype=" + or.getAssociation());
					if (or.getId().equals(sourceDatasetObjectID) && or.getAssociation().equals(linkType) ) {
						isLinkedAlready=true;
						if (removeLink) {
							remIndex = index;
						}
						existingLinks++;
					}
					index++;
										
				}  // iterator - items in the returned 'page'
				// if we get here - the object is not already linked...
				if (removeLink) {
					int removed=0;
					if (isLinkedAlready) { 
							System.out.println("\tremoving link..." + remIndex);
							logWriter.println("\tremoving link..." + remIndex);
							request.getSrcLinks().remove(remIndex);		
							String ifMatch;
							try {
								ifMatch = APIUtils.READER.catalogDataObjectsIdGetWithHttpInfo(targetDatasetObjectID).getHeaders().get("ETag").get(0);
							
								ObjectResponse newor=APIUtils.WRITER.catalogDataObjectsIdPut(targetDatasetObjectID, request, ifMatch);
								System.out.println("\tLink Removed between:"+sourceDatasetObjectID+" AND "+targetDatasetObjectID);
								logWriter.println("\tLink Removed between:"+sourceDatasetObjectID+" AND "+targetDatasetObjectID);
								removed++;
								deletedLinks++;
							} catch (ApiException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							
					}
					System.out.println("\tinks removed... "  + removed);
					return;
				}
				
				
				
			} catch (Exception ex) {
				System.out.println("Error finding target object: " + targetDatasetObjectID + " message: " + ex.getMessage());
				logWriter.println("Error finding target object: " + targetDatasetObjectID + " message: " + ex.getMessage());
				if (apiResponse != null) {
					System.out.println(apiResponse.getStatusCode());
				}
	//			ex.printStackTrace();
				
			}
	
			try {
	//			System.out.println("\t\t\tvalidating source object: " + sourceDatasetObjectID);			
				fromObjectResp=APIUtils.READER.catalogDataObjectsIdGetWithHttpInfo(sourceDatasetObjectID);
				
			} catch (Exception ex) {
				System.out.println("Error finding source object: " + sourceDatasetObjectID + " message: " + ex.getMessage());
				logWriter.println("Error finding source object: " + sourceDatasetObjectID + " message: " + ex.getMessage());
				if (apiResponse != null) {
					System.out.println(apiResponse.getStatusCode());
				}
	//			ex.printStackTrace();
				
			}
			
			if (apiResponse == null || fromObjectResp == null) {
				// exit
				System.out.println("no links created - cannot find source or target object");
				logWriter.println("no links created - cannot find source or target object");
				return;
			}
	
			try {
				link=new LinkedObjectRequest();
				link.setAssociation(linkType);
				link.setId(sourceDatasetObjectID);
				
				request.addSrcLinksItem(link);
	
				
	//			System.out.println("json=<<");
	//			System.out.println(request.toString());
	//			request.
				System.out.println("\t\t\talreadyLinked:" + isLinkedAlready);
				logWriter.println("\t\t\talreadyLinked:" + isLinkedAlready);
	//			System.out.println("json=>>");
	//			System.out.println(link.toString());
				
				if (!isLinkedAlready) {
					String ifMatch;
					try {
						ifMatch = APIUtils.READER.catalogDataObjectsIdGetWithHttpInfo(targetDatasetObjectID).getHeaders().get("ETag").get(0);
					
						ObjectResponse newor=APIUtils.WRITER.catalogDataObjectsIdPut(targetDatasetObjectID, request, ifMatch);
						System.out.println("Link Added between:"+sourceDatasetObjectID+" AND "+targetDatasetObjectID + " ifMatch=" + ifMatch);
						logWriter.println("Link Added between:"+sourceDatasetObjectID+" AND "+targetDatasetObjectID + " ifMatch=" + ifMatch);
						totalLinks++;
					} catch (ApiException e) {
						// TODO Auto-generated catch block
						System.out.println("error reading from object to get ETag: id=");
						logWriter.println("error reading from object to get ETag: id=");
						e.printStackTrace();
					}
				}
				
				
			} catch (Exception ex) {
				System.out.println("Error finding target object: " + targetDatasetObjectID + " message: " + ex.getMessage());
				logWriter.println("Error finding target object: " + targetDatasetObjectID + " message: " + ex.getMessage());
				if (apiResponse != null) {
					System.out.println(apiResponse.getStatusCode());
				}
	//			ex.printStackTrace();
				
			}
		
		
		} // test only
	}




}
