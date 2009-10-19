package org.navalplanner.web.resources.machine;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.validator.ClassValidator;
import org.hibernate.validator.InvalidValue;
import org.navalplanner.business.common.exceptions.ValidationException;
import org.navalplanner.business.resources.daos.ICriterionTypeDAO;
import org.navalplanner.business.resources.daos.IResourceDAO;
import org.navalplanner.business.resources.entities.Criterion;
import org.navalplanner.business.resources.entities.CriterionSatisfaction;
import org.navalplanner.business.resources.entities.CriterionType;
import org.navalplanner.business.resources.entities.CriterionWithItsType;
import org.navalplanner.business.resources.entities.ICriterionType;
import org.navalplanner.business.resources.entities.Interval;
import org.navalplanner.business.resources.entities.Resource;
import org.navalplanner.business.resources.entities.ResourceEnum;
import org.navalplanner.web.resources.worker.CriterionSatisfactionDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zkoss.zk.ui.WrongValueException;

/**
 *
 * @author Susana Montes Pedreira <smontes@wirelessgalicia.com>
 * @author Diego Pino Garcia <dpino@igalia.com>
 */

@Service()
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class AssignedMachineCriterionsModel implements IAssignedMachineCriterionsModel {

    private ClassValidator<CriterionSatisfactionDTO> satisfactionValidator = new ClassValidator<CriterionSatisfactionDTO>(
            CriterionSatisfactionDTO.class);

    @Autowired
    private IResourceDAO resourceDAO;

    @Autowired
    private ICriterionTypeDAO criterionTypeDAO;

    private List<CriterionWithItsType> criterionsWithItsTypes = new ArrayList<CriterionWithItsType>();

    private Resource resource;

    private Set<CriterionSatisfactionDTO> criterionSatisfactionDTOs = new HashSet<CriterionSatisfactionDTO>();

    private static List<ResourceEnum> applicableResources = new ArrayList<ResourceEnum>();

    static {
        applicableResources.add(ResourceEnum.RESOURCE);
        applicableResources.add(ResourceEnum.MACHINE);
    }

    @Override
    @Transactional(readOnly = true)
    public void prepareForEdit(Resource resource) {
        this.resource = resource;
        if (resource != null) {
            reattachmentResource();
            initDTOs();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void reattachmentResource() {
        resourceDAO.save(resource);
        initializeCriterionSatisfactions(resource.getCriterionSatisfactions());
    }

    private void initializeCriterionSatisfactions(
            Set<CriterionSatisfaction> criterionsSatisfaction) {
        for (CriterionSatisfaction criterionSatisfaction : criterionsSatisfaction) {
            initializeCriterionSatisfaction(criterionSatisfaction);
        }
    }

    private void initializeCriterionSatisfaction(
            CriterionSatisfaction criterionSatisfaction) {
        initializeCriterion(criterionSatisfaction.getCriterion());
    }

    private void initializeCriterion(Criterion criterion) {
        criterion.getName();
        if (criterion.getParent() != null) {
            criterion.getParent().getName();
        }
        reattachCriterionType(criterion.getType());
    }

    private void reattachCriterionType(CriterionType criterionType) {
        criterionTypeDAO.save(criterionType);
    }

    private void initDTOs() {
        criterionSatisfactionDTOs = new HashSet<CriterionSatisfactionDTO>();
        for (CriterionSatisfaction criterionSatisfaction : resource
                .getCriterionSatisfactions()) {
            if (!criterionSatisfaction.isIsDeleted()) {
                CriterionSatisfactionDTO dto = new CriterionSatisfactionDTO(
                        criterionSatisfaction);
                criterionSatisfactionDTOs.add(dto);
            }
        }
    }

    @Override
    public Set<CriterionSatisfactionDTO> getAllCriterionSatisfactions() {
        if (resource == null) {
            return new HashSet<CriterionSatisfactionDTO>();
        }
        return allSatisfactionsDTO();
    }

    @Override
    public Set<CriterionSatisfactionDTO> getFilterCriterionSatisfactions() {
        if (resource == null) {
            return new HashSet<CriterionSatisfactionDTO>();
        }
        return filterSatisfactionsDTO();
    }

    @Override
    public void addCriterionSatisfaction() {
        CriterionSatisfactionDTO criterionSatisfactionDTO = new CriterionSatisfactionDTO();
        this.criterionSatisfactionDTOs.add(criterionSatisfactionDTO);
    }

    private Set<CriterionSatisfactionDTO> allSatisfactionsDTO() {
        Set<CriterionSatisfactionDTO> satisfactions = new HashSet<CriterionSatisfactionDTO>();
        for (CriterionSatisfactionDTO criterionSatisfactionDTO : criterionSatisfactionDTOs) {
            if (!criterionSatisfactionDTO.isIsDeleted()) {
                satisfactions.add(criterionSatisfactionDTO);
            }
        }
        return satisfactions;
    }

    private Set<CriterionSatisfactionDTO> filterSatisfactionsDTO() {
        Set<CriterionSatisfactionDTO> satisfactions = new HashSet<CriterionSatisfactionDTO>();
        for (CriterionSatisfactionDTO criterionSatisfactionDTO : criterionSatisfactionDTOs) {
            if ((!criterionSatisfactionDTO.isIsDeleted())
                    && (criterionSatisfactionDTO.isCurrent())) {
                satisfactions.add(criterionSatisfactionDTO);
            }
        }
        return satisfactions;
    }

    @Override
    public void remove(CriterionSatisfactionDTO criterionSatisfactionDTO) {
        if (criterionSatisfactionDTO.isIsNewObject()) {
            criterionSatisfactionDTOs.remove(criterionSatisfactionDTO);
        } else {
            criterionSatisfactionDTO.setIsDeleted(true);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<CriterionWithItsType> getCriterionWithItsType() {
        criterionsWithItsTypes = new ArrayList<CriterionWithItsType>();
        List<CriterionType> listTypes = getCriterionTypes();
        for (CriterionType criterionType : listTypes) {
            Set<Criterion> listCriterion = getDirectCriterions(criterionType);
            getCriterionWithItsType(criterionType, listCriterion);
        }
        return criterionsWithItsTypes;
    }

    @Transactional(readOnly = true)
    private List<CriterionType> getCriterionTypes() {
        return criterionTypeDAO
                .getCriterionTypesByResources(applicableResources);
    }

    private void getCriterionWithItsType(CriterionType type,
            Set<Criterion> children) {
        for (Criterion criterion : children) {
            // Create the criterion with its criterionType and its Hierarchy
            // label
            CriterionWithItsType criterionAndType = new CriterionWithItsType(
                    type, criterion);

            // Add to the list
            criterionsWithItsTypes.add(criterionAndType);
            getCriterionWithItsType(type, criterion.getChildren());
        }
    }

    private static Set<Criterion> getDirectCriterions(
            CriterionType criterionType) {
        Set<Criterion> criterions = new HashSet<Criterion>();
        for (Criterion criterion : criterionType.getCriterions()) {
            if (criterion.getParent() == null) {
                criterions.add(criterion);
            }
        }
        return criterions;
    }

    @Override
    public void setCriterionWithItsType(
            CriterionSatisfactionDTO criterionSatisfactionDTO,
            CriterionWithItsType criterionAndType) throws WrongValueException {
        criterionSatisfactionDTO.setCriterionWithItsType(criterionAndType);
    }

    @Override
    public boolean checkSameCriterionAndSameInterval(
            CriterionSatisfactionDTO satisfaction) {
        return existSameCriterionAndInterval(satisfaction);
    }

    @Override
    public boolean checkNotAllowSimultaneousCriterionsPerResource(
            CriterionSatisfactionDTO satisfaction) {
        ICriterionType<?> type = satisfaction.getCriterionWithItsType()
                .getType();
        if (type.isAllowSimultaneousCriterionsPerResource()) {
            return false;
        }
        return existSameCriterionTypeAndInterval(satisfaction);
    }

    private boolean existSameCriterionTypeAndInterval(
            CriterionSatisfactionDTO satisfaction) {
        for (CriterionSatisfactionDTO otherSatisfaction : criterionSatisfactionDTOs) {
            if ((!otherSatisfaction.equals(satisfaction))
                    && (sameCriterionType(otherSatisfaction, satisfaction))
                    && (sameInterval(otherSatisfaction, satisfaction))) {
                return true;
            }
        }
        return false;
    }

    private boolean existSameCriterionAndInterval(
            CriterionSatisfactionDTO satisfaction) {
        for (CriterionSatisfactionDTO otherSatisfaction : criterionSatisfactionDTOs) {
            if ((!otherSatisfaction.equals(satisfaction))
                    && (sameCriterion(otherSatisfaction, satisfaction))
                    && (sameInterval(otherSatisfaction, satisfaction))) {
                return true;
            }
        }
        return false;
    }

    private boolean sameCriterion(CriterionSatisfactionDTO otherSatisfaction,
            CriterionSatisfactionDTO satisfaction) {
        if (otherSatisfaction.getCriterionWithItsType() == null)
            return false;
        Criterion otherCriterion = otherSatisfaction.getCriterionWithItsType()
                .getCriterion();
        if (otherCriterion.getId().equals(
                satisfaction.getCriterionWithItsType().getCriterion().getId()))
            return true;
        return false;
    }

    private boolean sameCriterionType(
            CriterionSatisfactionDTO otherSatisfaction,
            CriterionSatisfactionDTO satisfaction) {
        if (otherSatisfaction.getCriterionWithItsType() == null)
            return false;
        ICriterionType<?> criterionType = otherSatisfaction
                .getCriterionWithItsType().getType();
        if (criterionType.equals(satisfaction.getCriterionWithItsType()
                .getType()))
            return true;
        return false;
    }

    private boolean sameInterval(CriterionSatisfactionDTO otherSatisfaction,
            CriterionSatisfactionDTO satisfaction) {
        if (otherSatisfaction.getStartDate() == null)
            return false;
        Interval otherInterval = otherSatisfaction.getInterval();
        if (satisfaction.overlapsWith(otherInterval))
            return true;
        return false;
    }

    public void save() throws ValidationException {
        InvalidValue[] invalidValues;
        for (CriterionSatisfactionDTO satisfactionDTO : this.criterionSatisfactionDTOs) {
            invalidValues = satisfactionValidator
                    .getInvalidValues(satisfactionDTO);
            if (invalidValues.length > 0)
                throw new ValidationException(invalidValues);
            save(satisfactionDTO);
        }
    }

    private void save(CriterionSatisfactionDTO satisfactionDTO) {
        if (satisfactionDTO.isIsNewObject()) {
            addNewSatisfaction(satisfactionDTO);
        } else {
            if (satisfactionDTO.isIsDeleted()) {
                removeSatisfaction(satisfactionDTO);
            } else {
                updateSatisfaction(satisfactionDTO);
            }
        }
    }

    private void addNewSatisfaction(CriterionSatisfactionDTO satisfactionDTO) {
        Interval interval = satisfactionDTO.getInterval();
        resource.addSatisfaction(satisfactionDTO.getCriterionWithItsType(),
                interval);
    }

    private void removeSatisfaction(CriterionSatisfactionDTO satisfactionDTO) {
        CriterionSatisfaction satisfaction = satisfactionDTO
                .getCriterionSatisfaction();
        satisfaction.setIsDeleted(true);
    }

    private void updateSatisfaction(CriterionSatisfactionDTO satisfactionDTO) {
        CriterionSatisfaction satisfaction = satisfactionDTO
                .getCriterionSatisfaction();
        Criterion newCriterion = satisfactionDTO.getCriterionWithItsType()
                .getCriterion();
        Date newStartDate = satisfactionDTO.getStartDate();
        Date newEndDate = satisfactionDTO.getEndDate();
        satisfaction.setCriterion(newCriterion);
        satisfaction.setStartDate(newStartDate);
        satisfaction.setEndDate(newEndDate);
    }

}
