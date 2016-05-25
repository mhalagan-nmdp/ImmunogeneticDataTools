package org.dash.valid.freq;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.dash.valid.DisequilibriumElement;
import org.dash.valid.Linkages;
import org.dash.valid.LinkagesLoader;
import org.dash.valid.Locus;
import org.dash.valid.base.BaseDisequilibriumElement;
import org.dash.valid.gl.GLStringConstants;
import org.dash.valid.gl.GLStringUtilities;
import org.dash.valid.race.DisequilibriumElementByRace;
import org.dash.valid.race.FrequencyByRace;
import org.dash.valid.race.FrequencyByRaceComparator;

public class HLAFrequenciesLoader {	
	private HashMap<EnumSet<Locus>, List<DisequilibriumElement>> disequilibriumElementsMap = new HashMap<EnumSet<Locus>, List<DisequilibriumElement>>();
	private final HashMap<Locus, List<String>> individualLocusFrequencies = new HashMap<Locus, List<String>>();
	
	private static final String UNDERSCORE = "_";
	
	private static final String WIKIVERSITY_BC_FREQUENCIES = "frequencies/wikiversity/BCLinkageDisequilibrium.txt";
	private static final String WIKIVERSITY_DRDQ_FREQUENCIES = "frequencies/wikiversity/DRDQLinkageDisequilibrium.txt";
	
	private static final String NMDP_ABC_FREQUENCIES = "frequencies/nmdp/A~C~B.xlsx";
	private static final String NMDP_BC_FREQUENCIES = "frequencies/nmdp/C~B.xlsx";
	private static final String NMDP_DRDQ_FREQUENCIES = "frequencies/nmdp/DRB3-4-5~DRB1~DQB1.xlsx";
	private static final String NMDP_FIVE_LOCUS_FREQUENCIES = "frequencies/nmdp/A~C~B~DRB1~DQB1.xlsx";
	
	private static final String NMDP_2007_ABC_FREQUENCIES = "frequencies/nmdp-2007/ACB.xls";
	private static final String NMDP_2007_BC_FREQUENCIES = "frequencies/nmdp-2007/CB.xls";
	private static final String NMDP_2007_DRB1DQB1_FREQUENCIES = "frequencies/nmdp-2007/DRB1DQB1.xls";
	private static final String NMDP_2007_FIVE_LOCUS_FREQUENCIES = "frequencies/nmdp-2007/ACBDRB1DQB1.xls";
	
	private static final String NMDP_STD_FIVE_LOCUS_FREQUENCIES = "frequencies/nmdp-std/2015_ALL.csv";
	
	private static final Locus[] BASE_BC_LOCI_POS = new Locus[] {Locus.HLA_B, Locus.HLA_C};
	private static final Locus[] BASE_DRDQ_LOCI_POS = new Locus[] {Locus.HLA_DRB1, Locus.HLA_DRB345, Locus.HLA_DQA1, Locus.HLA_DQB1};
	private static final Locus[] NMDP_ABC_LOCI_POS = new Locus[] {Locus.HLA_A, Locus.HLA_C, Locus.HLA_B};
	private static final Locus[] NMDP_BC_LOCI_POS = new Locus[] {Locus.HLA_C, Locus.HLA_B};
	private static final Locus[] NMDP_DRB1DQB1_LOCI_POS = new Locus[] {Locus.HLA_DRB1, Locus.HLA_DQB1};
	private static final Locus[] NMDP_DRDQB1_LOCI_POS = new Locus[] {Locus.HLA_DRB345, Locus.HLA_DRB1, Locus.HLA_DQB1};
	private static final Locus[] NMDP_FIVE_LOCUS_POS = new Locus[] {Locus.HLA_A, Locus.HLA_C, Locus.HLA_B, Locus.HLA_DRB1, Locus.HLA_DQB1};
	
	private static HLAFrequenciesLoader instance = null;
		
    private static final Logger LOGGER = Logger.getLogger(HLAFrequenciesLoader.class.getName());
    
    private HLAFrequenciesLoader() {
    	
    }
    
	public static HLAFrequenciesLoader getInstance() throws IOException {
		if (instance == null) {
			instance = new HLAFrequenciesLoader();
			Frequencies freq = Frequencies.lookup(System.getProperty(Frequencies.FREQUENCIES_PROPERTY));
						
			instance.init(freq);
		}
		
		return instance;
	}
	
	public boolean hasIndividualFrequency(Locus locus) {
		HashMap<Locus, List<String>> individualFrequencies = getIndividualLocusFrequencies();
		
		if (individualFrequencies != null && individualFrequencies.size() > 0 && individualFrequencies.get(locus) != null) {
			return true;
		}
		
		return false;
	}
	
	public String hasFrequency(Locus locus, String allele) {
		HashMap<Locus, List<String>> individualFrequencies = getIndividualLocusFrequencies();
		if (individualFrequencies == null || individualFrequencies.get(locus) == null) {
			return null;
		}
		
		for (String alleleWithFrequency : individualFrequencies.get(locus)) {
			if (GLStringUtilities.fieldLevelComparison(allele, alleleWithFrequency) != null || 
					GLStringUtilities.checkAntigenRecognitionSite(allele, alleleWithFrequency) != null) {
				return alleleWithFrequency;
			}
		}
		
		return null;
	}
	
	public void reloadFrequencies() throws IOException {
		Frequencies freq = Frequencies.lookup(System.getProperty(Frequencies.FREQUENCIES_PROPERTY));
		
		this.disequilibriumElementsMap = new HashMap<EnumSet<Locus>, List<DisequilibriumElement>>();

		init(freq);
	}
	
	private void init(Frequencies freq) throws IOException {
		try {
			switch(freq) {
			case NMDP_2007:
				for (Linkages linkage : LinkagesLoader.getInstance().getLinkages()) {
					switch (linkage) {
					case A_B_C:
						this.disequilibriumElementsMap.put(Locus.A_B_C_LOCI, loadNMDPLinkageReferenceData(NMDP_2007_ABC_FREQUENCIES, NMDP_ABC_LOCI_POS));
					case B_C:
						this.disequilibriumElementsMap.put(Locus.B_C_LOCI, loadNMDPLinkageReferenceData(NMDP_2007_BC_FREQUENCIES, NMDP_BC_LOCI_POS));
					case DRB1_DQB1:
						this.disequilibriumElementsMap.put(Locus.DRB1_DQB1_LOCI, loadNMDPLinkageReferenceData(NMDP_2007_DRB1DQB1_FREQUENCIES, NMDP_DRB1DQB1_LOCI_POS));
					case FIVE_LOCUS:
						this.disequilibriumElementsMap.put(Locus.FIVE_LOCUS, loadNMDPLinkageReferenceData(NMDP_2007_FIVE_LOCUS_FREQUENCIES, NMDP_FIVE_LOCUS_POS));
					default:
						break;
					}
				}
				loadIndividualLocusFrequencies(freq);
				break;
			case NMDP:
				for (Linkages linkage : LinkagesLoader.getInstance().getLinkages()) {
					switch (linkage) {
					case A_B_C:
						this.disequilibriumElementsMap.put(Locus.A_B_C_LOCI, loadNMDPLinkageReferenceData(NMDP_ABC_FREQUENCIES, NMDP_ABC_LOCI_POS));
					case B_C:
						this.disequilibriumElementsMap.put(Locus.B_C_LOCI, loadNMDPLinkageReferenceData(NMDP_BC_FREQUENCIES, NMDP_BC_LOCI_POS));
					case DRB_DQB:
						this.disequilibriumElementsMap.put(Locus.DRB_DQB_LOCI, loadNMDPLinkageReferenceData(NMDP_DRDQ_FREQUENCIES, NMDP_DRDQB1_LOCI_POS));
					case FIVE_LOCUS:
						this.disequilibriumElementsMap.put(Locus.FIVE_LOCUS,  loadNMDPLinkageReferenceData(NMDP_FIVE_LOCUS_FREQUENCIES, NMDP_FIVE_LOCUS_POS));
					default:
						break;
					}
				}
				loadIndividualLocusFrequencies(freq);
				break;
			case NMDP_STD:
				for (Linkages linkage : LinkagesLoader.getInstance().getLinkages()) {
					switch (linkage) {
					case FIVE_LOCUS:
						this.disequilibriumElementsMap.put(Locus.FIVE_LOCUS, loadStandardReferenceData(NMDP_STD_FIVE_LOCUS_FREQUENCIES));
					default:
						break;
					}
				}
				loadIndividualLocusFrequencies(freq);
				break;
			case WIKIVERSITY:
				for (Linkages linkage : LinkagesLoader.getInstance().getLinkages()) {
					switch (linkage) {
					case B_C:
						this.disequilibriumElementsMap.put(Locus.B_C_LOCI, loadLinkageReferenceData(WIKIVERSITY_BC_FREQUENCIES, BASE_BC_LOCI_POS));
					case DRB_DQ:
						this.disequilibriumElementsMap.put(Locus.DRB_DQ_LOCI, loadLinkageReferenceData(WIKIVERSITY_DRDQ_FREQUENCIES, BASE_DRDQ_LOCI_POS));
					default:
						break;
					}
				}
			}			
		}
		catch (IOException | InvalidFormatException ioe) {
			if (Frequencies.NMDP.equals(freq)) {
				LOGGER.warning("2011 NMDP Frequencies are not included by default.  Please be sure you've loaded them according to the instructions in the README.");
			}
			LOGGER.severe("Couldn't load disequilibrium element reference file.");
			ioe.printStackTrace();
			
			throw new IOException(ioe);
		}
	}
	
	public List<DisequilibriumElement> getDisequilibriumElements(EnumSet<Locus> loci) {
		if (this.disequilibriumElementsMap.containsKey(loci)) {
			return this.disequilibriumElementsMap.get(loci);
		}
		
		return new ArrayList<DisequilibriumElement>();
	}
	
	public HashMap<Locus, List<String>> getIndividualLocusFrequencies() {
		return individualLocusFrequencies;
	}
	
	private List<DisequilibriumElement> loadStandardReferenceData(String filename) throws IOException, InvalidFormatException {
		InputStream is = HLAFrequenciesLoader.class.getClassLoader().getResourceAsStream(filename);
		InputStreamReader isr = new InputStreamReader(is);
		BufferedReader reader = new BufferedReader(isr);

		String row;
		String[] columns;
		HashMap<String, List<FrequencyByRace>> frequencyMap = new HashMap<String, List<FrequencyByRace>>();
		
		while ((row = reader.readLine()) != null) {			
			columns = row.split(GLStringConstants.COMMA);
						
			String race = columns[0];
			String haplotype = columns[1];
			Double frequency = new Double(columns[2]);
												
			List<FrequencyByRace> freqList = frequencyMap.get(haplotype);
			
			if (freqList == null) {
				freqList = new ArrayList<FrequencyByRace>();
			}
			
			FrequencyByRace freqByRace = new FrequencyByRace(frequency, null, race);
			freqList.add(freqByRace);
			
			frequencyMap.put(haplotype, freqList);
		}
		
		List<DisequilibriumElement> disequilibriumElements = new ArrayList<DisequilibriumElement>();
		DisequilibriumElementByRace disElement;
		
		for (String haplotype : frequencyMap.keySet()) {
			String[] locusHaplotypes = haplotype.split(GLStringConstants.GENE_PHASE_DELIMITER);
			
			HashMap<Locus, String> hlaElementMap = new HashMap<Locus, String>();
			for (String locusHaplotype : locusHaplotypes) {
				String[] parts = locusHaplotype.split(GLStringUtilities.ESCAPED_ASTERISK);
				hlaElementMap.put(Locus.lookup(parts[0]), locusHaplotype);
			}
			disElement = new DisequilibriumElementByRace(hlaElementMap, frequencyMap.get(haplotype));
			
			disequilibriumElements.add(disElement);
		}
		
		reader.close();
		
		return disequilibriumElements;
	}
	
	private List<DisequilibriumElement> loadNMDPLinkageReferenceData(String filename, Locus[] locusPositions) throws IOException, InvalidFormatException {  
		List<DisequilibriumElement> disequilibriumElements = new ArrayList<DisequilibriumElement>();
		
        // Finds the workbook instance for XLSX file
		InputStream inStream = HLAFrequenciesLoader.class.getClassLoader().getResourceAsStream(filename);
		
		if (inStream == null) {
			throw new FileNotFoundException();
		}
		
        Workbook workbook = WorkbookFactory.create(inStream);
       
        // Return first sheet from the XLSX workbook
        Sheet mySheet = workbook.getSheetAt(0);
       
        // Get iterator to all the rows in current sheet
        Iterator<Row> rowIterator = mySheet.iterator();
        
        int firstRow = mySheet.getFirstRowNum();
        
        List<String> raceHeaders = null;

        // Traversing over each row of XLSX file
        while (rowIterator.hasNext()) {
            Row row = rowIterator.next();

            if (row.getRowNum() == firstRow) {
            	raceHeaders = readHeaderElementsByRace(row);
            }
            else {
	            disequilibriumElements.add(readDiseqilibriumElementsByRace(row, raceHeaders, locusPositions));
            }            
        }
        
        workbook.close();
        
        return disequilibriumElements;
	}
	
	private void loadIndividualLocusFrequencies(Frequencies freq) throws IOException, InvalidFormatException {
		for (Linkages linkage : LinkagesLoader.getInstance().getLinkages()) {
			for (Locus locus : linkage.getLoci()) {
				if (locus.hasIndividualFrequencies()) {
					loadIndividualLocusFrequency(freq, locus);
				}
			}
		}
	}

	private void loadIndividualLocusFrequency(Frequencies freq, Locus locus)
			throws IOException, InvalidFormatException {
		List<String> singleLocusFrequencies = new ArrayList<String>();
		String extension = freq.equals(Frequencies.NMDP) ? ".xlsx" : ".xls";
		InputStream inputStream = HLAFrequenciesLoader.class.getClassLoader().getResourceAsStream("frequencies/" + freq.getShortName() + "/" + locus.getFrequencyName() + extension);
      
		if (inputStream == null) return;
		
		Workbook workbook = WorkbookFactory.create(inputStream);
		
		// Return first sheet from the XLSX workbook
		Sheet mySheet = workbook.getSheetAt(0);
      
		// Get iterator to all the rows in current sheet
		Iterator<Row> rowIterator = mySheet.iterator();
		
		int firstRow = mySheet.getFirstRowNum();
			
		String cellValue = null;
		
		// Traversing over each row of XLSX file
		while (rowIterator.hasNext()) {
		    Row row = rowIterator.next();

		    if (row.getRowNum() == firstRow) {
		    	continue;
		    }
		    else {
			    cellValue = row.getCell(0).getStringCellValue();
			    if (!cellValue.contains(GLStringConstants.ASTERISK)) {
			    	cellValue = locus.getShortName() + GLStringConstants.ASTERISK + cellValue.substring(0, 2) + GLStringUtilities.COLON + cellValue.substring(2);
			    }
				singleLocusFrequencies.add(GLStringConstants.HLA_DASH + cellValue);
		    }            
		}
		
		individualLocusFrequencies.put(locus,  singleLocusFrequencies);
		
		workbook.close();
	}
	
	private List<String> readHeaderElementsByRace(Row row) {		
		List<String> raceHeaders = new ArrayList<String>();
		
		Iterator<Cell> cellIterator = row.cellIterator();

		while (cellIterator.hasNext()) {
			Cell cell = cellIterator.next();
			
			String[] race = cell.getStringCellValue().split(UNDERSCORE);
			raceHeaders.add(cell.getColumnIndex(), race[0]);
		}
		
		return raceHeaders;
	}

	/**
	 * @param row
	 */
	private DisequilibriumElement readDiseqilibriumElementsByRace(Row row, List<String> raceHeaders, Locus[] locusPositions) {		
		// For each row, iterate through each columns
		Iterator<Cell> cellIterator = row.cellIterator();
		
		List<FrequencyByRace> frequenciesByRace  = new ArrayList<FrequencyByRace>();
		DisequilibriumElementByRace disElement = new DisequilibriumElementByRace();
		
		int columnIndex;
		String cellValue = null;
		
		while (cellIterator.hasNext()) {
		    Cell cell = cellIterator.next();

		    columnIndex = cell.getColumnIndex();
		    
		    if (columnIndex < locusPositions.length) {
			    cellValue = cell.getStringCellValue();
			    if (!cellValue.contains(GLStringConstants.ASTERISK)) {
			    	cellValue = locusPositions[columnIndex].getShortName() + GLStringConstants.ASTERISK + cellValue.substring(0, 2) + GLStringUtilities.COLON + cellValue.substring(2);
			    }
		    	disElement.setHlaElement(locusPositions[columnIndex], GLStringConstants.HLA_DASH + cellValue);
		    }
		    else {
		    	if ((locusPositions.length % 2 == 0 && columnIndex % 2 == 0) || (locusPositions.length % 2 != 0 && columnIndex % 2 != 0)) {
		    		disElement.setFrequenciesByRace(loadFrequencyAndRank(row, cell, frequenciesByRace, raceHeaders));
		    	}
		    }
		}
		
	    return disElement;
	}

	/**
	 * @param row
	 * @param frequenciesByRace
	 * @param cell
	 * @param idx
	 */
	private List<FrequencyByRace> loadFrequencyAndRank(Row row, Cell cell, 
			List<FrequencyByRace> frequenciesByRace, List<String> raceHeaders) {
		Double freq = cell.getNumericCellValue();
		
		if (freq != 0) {
			FrequencyByRace frequencyByRace = new FrequencyByRace(freq, ((Double) row.getCell(cell.getColumnIndex() + 1).getNumericCellValue()).toString(), raceHeaders.get(cell.getColumnIndex()));
			frequenciesByRace.add(frequencyByRace);
		}
		
		Collections.sort(frequenciesByRace, new FrequencyByRaceComparator());
		
		return frequenciesByRace;
	}
	
	private List<DisequilibriumElement> loadLinkageReferenceData(String filename, Locus[] locusPositions) throws FileNotFoundException, IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(HLAFrequenciesLoader.class.getClassLoader().getResourceAsStream(filename)));
		String row;
		String[] columns;
		HashMap<Locus, String> hlaElementMap;
		List<DisequilibriumElement> disequilibriumElements = new ArrayList<DisequilibriumElement>();
		
		while ((row = reader.readLine()) != null) {
			hlaElementMap = new HashMap<Locus, String>();
			
			columns = row.split(GLStringConstants.TAB);
			
			for (int i=0;i<locusPositions.length;i++) {
				hlaElementMap.put(locusPositions[i],  columns[i]);
			}
			
			disequilibriumElements.add(new BaseDisequilibriumElement(hlaElementMap, columns[locusPositions.length], columns[locusPositions.length + 1]));
		}
		
		reader.close();
		
		return disequilibriumElements;
	}
}
