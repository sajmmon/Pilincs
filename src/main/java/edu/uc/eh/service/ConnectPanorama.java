package edu.uc.eh.service;


import edu.uc.eh.domain.*;
import org.labkey.remoteapi.CommandException;
import org.labkey.remoteapi.Connection;
import org.labkey.remoteapi.query.SelectRowsCommand;
import org.labkey.remoteapi.query.SelectRowsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

/**
 * Created by chojnasm on 7/13/15.
 */

@Service
public class ConnectPanorama {

    private static final Logger log = LoggerFactory.getLogger(ConnectPanorama.class);

    @Value("${panorama.folders}")
    private String panoramaFolders;

    @Value("${panorama.runIdUrl}")
    private String runIdUrl;

    @Value("${panorama.gctUrl}")
    private String gctUrl;

    @Value("${panorama.connectionUrl}")
    private String panoramaConnectionUrl;

    @Value("${panorama.detailedLink1}")
    private String intermediateLink;

    @Value("${panorama.detailedLink2}")
    private String detailedLink;

    public List<String> GctUrls() throws IOException, CommandException {


        List<String> output = new ArrayList<>();
        String[] folderNames = panoramaFolders.split(",");
        String gcpOrP100;

        for(String folderName:folderNames) {
            for (Integer runId : getRunIds(folderName)) {
                gcpOrP100 = folderName.contains("GCP") ? "GCP" : "P100";
                output.add(String.format(gctUrl,gcpOrP100,runId));
            }
        }
        return output;
    }

    private List<Integer> getRunIds(String folderName) throws IOException, CommandException {
        ArrayList<Integer> runIds = new ArrayList<Integer>();
        Connection cn = new Connection(panoramaConnectionUrl);

        SelectRowsCommand cmd = new SelectRowsCommand("targetedms", "runs");
        cmd.getColumns().addAll(Arrays.asList("Id", "Description"));

        SelectRowsResponse response = cmd.execute(cn, folderName);
        List<Map<String, Object>> rows = response.getRows();

        for (Map<String, Object> row : rows) {
            if(row.get("Description").toString().contains("QC"))continue; // skip Quality Control files
            runIds.add((Integer) row.get("Id"));
        }
        return runIds;
    }

    public String getSourceUrl(PeakArea peakArea) {

        AssayType assayType = peakArea.getGctFile().getAssayType();
        int runId = peakArea.getGctFile().getRunId();
        String escapedPeptideId = peakArea.getPeptideAnnotation().escapedPeptideId();
        String replicateId = peakArea.getReplicateAnnotation().getReplicateId();

        String stepOne = String.format(intermediateLink,assayType,escapedPeptideId,runId);

        Integer peptide = NetworkUtils.parsePeptideNumber(stepOne);

        if(peptide!=null){
            return String.format(detailedLink,assayType,peptide,replicateId);
        }
            return null;
    }

    public String getRunIdLink(GctFile gctFile){

        AssayType assayType = gctFile.getAssayType();
        int runId = gctFile.getRunId();

        return String.format(runIdUrl,assayType,runId);
    }

}
