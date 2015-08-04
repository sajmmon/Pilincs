package edu.uc.eh.service;

import edu.uc.eh.domain.*;
import edu.uc.eh.domain.repository.*;

import edu.uc.eh.utils.Tuples;
import edu.uc.eh.utils.Utils;
import org.labkey.remoteapi.CommandException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.awt.print.Pageable;
import java.io.IOException;
import java.util.*;

/**
 * Created by chojnasm on 7/14/15.
 */

@Service
public class DatabaseLoader {

    private final ConnectPanorama connectPanorama;
    private final ParseGCT parser;
    private final GctFileRepository gctFileRepository;
    private final PeakAreaRepository peakAreaRepository;
    private final PeptideAnnotationRepository peptideAnnotationRepository;
    private final ReplicateAnnotationRepository replicateAnnotationRepository;
    private final ProfileRepository profileRepository;

    private static final Logger log = LoggerFactory.getLogger(DatabaseLoader.class);

    @Autowired
    public DatabaseLoader(ConnectPanorama connectPanorama,
                          ParseGCT parser,
                          GctFileRepository gctFileRepository,
                          PeakAreaRepository peakAreaRepository,
                          PeptideAnnotationRepository peptideAnnotationRepository,
                          ReplicateAnnotationRepository replicateAnnotationRepository,
                          ProfileRepository profileRepository) {
        this.connectPanorama = connectPanorama;
        this.parser = parser;
        this.gctFileRepository = gctFileRepository;
        this.peakAreaRepository = peakAreaRepository;
        this.peptideAnnotationRepository = peptideAnnotationRepository;
        this.replicateAnnotationRepository = replicateAnnotationRepository;
        this.profileRepository = profileRepository;
    }

    @PostConstruct
    private void initDatabase() throws IOException, CommandException {
        loadRawData();
        loadProfiles();
    }

    private void loadProfiles() {
        log.info("Filling repository with profiles");
        for(ReplicateAnnotation replicateAnnotation : replicateAnnotationRepository.findAll()){
            Long replicateId = replicateAnnotation.getId();
            List<PeakArea> peaksForReplicate = peakAreaRepository.findByReplicateAnnotationId(replicateId);
            List<Tuples.Tuple2<java.lang.String,Double>> profileVector = new ArrayList<>();

            for(PeakArea peakArea : peaksForReplicate){
                profileVector.add(new Tuples.Tuple2<>(peakArea.getPeptideAnnotation().getPeptideId(),peakArea.getValue()));
            }

            Profile profile = new Profile(replicateAnnotation, profileVector);

            profileRepository.save(profile);
        }

        PageRequest pageable = new PageRequest(0,10);
        List<String> cells = new ArrayList<>();
        cells.add("PC3");
        List<String> pertiname = new ArrayList<>();
        pertiname.add("methylstat");

        List<Profile> profiles =
                profileRepository.findByReplicateAnnotationCellIdInAndReplicateAnnotationPertinameIn(cells, pertiname, pageable);

        log.warn(profiles.size()+"");

    }

    private void loadRawData() throws IOException, CommandException {
        log.info("Loading raw data from panorama etc");
        List<String> list = connectPanorama.GctUrls();
        int counter = 0;
        for(String url : list){
//            if(counter++ > 0) continue;
            HashMap<String, List<ParseGCT.AnnotationValue>> metaProbes = new HashMap<>();
            HashMap<String, List<ParseGCT.AnnotationValue>> metaReplicas = new HashMap<>();
            ArrayList<ParseGCT.ProbeReplicatePeak> peakValues = new ArrayList<>();

            parser.parseToRepository(url, peakValues, metaProbes, metaReplicas);

//            Cache sourceUrls
            List<String> probeIds = new ArrayList<>(metaProbes.keySet());
            HashMap<String,Integer> dbPeptideIds = connectPanorama.getPeptideIds(probeIds,
                    Utils.parseArrayTypeFromUrl(url),
                    Utils.parseRunId(url)
                    );
//
            if(dbPeptideIds.size()!=probeIds.size()){
                log.warn("STOP, stop, stop!!!");
            }

            GctFile gctfile = new GctFile(url);
            gctfile.setRunId(Utils.parseRunId(url));
            gctfile.setRunIdUrl(connectPanorama.getRunIdLink(gctfile));
            gctFileRepository.save(gctfile);


            for(ParseGCT.ProbeReplicatePeak peak : peakValues) {

                String probeId = peak.getProbeId();
                String replicateId = peak.getReplicateId();

                PeptideAnnotation peptideAnnotation = peptideAnnotationRepository.findFirstByPeptideId(probeId);
                ReplicateAnnotation replicateAnnotation = replicateAnnotationRepository.findFirstByReplicateId(replicateId);

                if(peptideAnnotation == null){
                    peptideAnnotation = new PeptideAnnotation(probeId);
                    for(ParseGCT.AnnotationValue annotationObject : metaProbes.get(probeId)){
                        String annotationName = annotationObject.getAnnotationName();
                        String annotationValue = annotationObject.getAnnotationValue();

                        switch(annotationName){
                            case "pr_gene_id":
                                peptideAnnotation.setPrGeneId(annotationValue);
                                break;
                            case "pr_gene_symbol":
                            case "GeneName":
                                peptideAnnotation.setPrGeneSymbol(annotationValue);
                                break;
                            case "pr_p100_cluster":
                            case "pr_gcp_cluster":
                                peptideAnnotation.setPrCluster(annotationValue);
                                break;
                            case "pr_uniprot_id":
                                peptideAnnotation.setPrUniprotId(annotationValue);
                                break;
                            case "pr_p100_base_peptide":
                            case "pr_gcp_base_peptide":
                                peptideAnnotation.setPrBasePeptide(annotationValue);
                                break;
                            case "pr_gcp_histone_mark":
                                peptideAnnotation.setPrHistoneMark(annotationValue);
                                break;
                            case "pr_gcp_modified_peptide_code":
                            case "pr_p100_modified_peptide_code":
                                peptideAnnotation.setPrModifiedPeptideCode(annotationValue);
                                break;
                            default:
//                                log.warn("New annotation: {}",annotationName);
                        }
                    }
                }

                if(replicateAnnotation == null){
                    replicateAnnotation = new ReplicateAnnotation(replicateId);
                    for(ParseGCT.AnnotationValue annotationObject : metaReplicas.get(replicateId)){
                        String annotationName = annotationObject.getAnnotationName();
                        String annotationValue = annotationObject.getAnnotationValue();

                        switch(annotationName){
                            case "cell_id":
                                replicateAnnotation.setCellId(annotationValue);
                                break;
                            case "det_plate":
                                replicateAnnotation.setDetPlate(annotationValue);
                                break;
                            case "det_well":
                                replicateAnnotation.setDetWell(annotationValue);
                                break;
                            case "pert_dose":
                                replicateAnnotation.setPertDose(annotationValue);
                                break;
                            case "pert_id":
                                replicateAnnotation.setPertId(annotationValue);
                                break;
                            case "pert_iname":
                                replicateAnnotation.setPertiname(annotationValue);
                                break;
                            case "pert_time":
                                replicateAnnotation.setPertTime(annotationValue);
                                break;
                            case "pert_type":
                                replicateAnnotation.setPertType(annotationValue);
                                break;
                            case "pert_vehicle":
                                replicateAnnotation.setPertVehicle(annotationValue);
                            case "pubchem_cid":
                                replicateAnnotation.setPubchemCid(annotationValue);
                                break;

                            default:
//                                log.warn("New annotation: {}",annotationName);
                        }
                    }
                }

                peptideAnnotationRepository.save(peptideAnnotation);
                replicateAnnotationRepository.save(replicateAnnotation);

                PeakArea peakArea = new PeakArea(gctfile,peptideAnnotation,replicateAnnotation,peak.getPeakArea());
                peakArea.setSourceUrl(connectPanorama.getSourceUrlFast(
                        Utils.parseArrayTypeFromUrl(url),
                        dbPeptideIds.get(peakArea.getPeptideAnnotation().getPeptideId()),
                        peakArea.getReplicateAnnotation().getReplicateId()));

                peakAreaRepository.save(peakArea);


            }
        }
    }

}
