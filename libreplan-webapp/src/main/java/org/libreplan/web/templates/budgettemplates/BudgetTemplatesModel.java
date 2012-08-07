/*
 * This file is part of LibrePlan
 *
 * Copyright (C) 2012 Igalia, S.L.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.libreplan.web.templates.budgettemplates;

import static org.libreplan.web.I18nHelper._;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.libreplan.business.advance.entities.AdvanceAssignmentTemplate;
import org.libreplan.business.common.IAdHocTransactionService;
import org.libreplan.business.common.IOnTransaction;
import org.libreplan.business.common.exceptions.InstanceNotFoundException;
import org.libreplan.business.common.exceptions.ValidationException;
import org.libreplan.business.labels.daos.ILabelDAO;
import org.libreplan.business.labels.entities.Label;
import org.libreplan.business.orders.daos.IOrderDAO;
import org.libreplan.business.orders.daos.IOrderElementDAO;
import org.libreplan.business.orders.entities.HoursGroup;
import org.libreplan.business.orders.entities.Order;
import org.libreplan.business.orders.entities.OrderElement;
import org.libreplan.business.orders.entities.OrderStatusEnum;
import org.libreplan.business.qualityforms.daos.IQualityFormDAO;
import org.libreplan.business.qualityforms.entities.QualityForm;
import org.libreplan.business.requirements.entities.DirectCriterionRequirement;
import org.libreplan.business.resources.daos.ICriterionDAO;
import org.libreplan.business.resources.daos.ICriterionTypeDAO;
import org.libreplan.business.resources.entities.Criterion;
import org.libreplan.business.resources.entities.CriterionType;
import org.libreplan.business.scenarios.IScenarioManager;
import org.libreplan.business.scenarios.entities.Scenario;
import org.libreplan.business.templates.daos.IOrderElementTemplateDAO;
import org.libreplan.business.templates.entities.Budget;
import org.libreplan.business.templates.entities.BudgetTemplate;
import org.libreplan.business.templates.entities.OrderElementTemplate;
import org.libreplan.web.common.concurrentdetection.OnConcurrentModification;
import org.libreplan.web.orders.QualityFormsOnConversation;
import org.libreplan.web.orders.labels.LabelsOnConversation;
import org.libreplan.web.planner.budget.IBudgetModel;
import org.libreplan.web.planner.order.PlanningStateCreator;
import org.libreplan.web.planner.order.PlanningStateCreator.IActionsOnRetrieval;
import org.libreplan.web.planner.order.PlanningStateCreator.PlanningState;
import org.libreplan.web.templates.OrderElementsOnConversation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.zkoss.zk.ui.Desktop;

/**
 * @author Óscar González Fernández <ogonzalez@igalia.com>
 * @author Diego Pino Garcia <dpino@igalia.com>
 * @author Jacobo Aragunde Pérez <jaragunde@igalia.com>
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@OnConcurrentModification(goToPage = "/budgettemplates/templates.zul")
public class BudgetTemplatesModel implements IBudgetTemplatesModel,
        IBudgetModel {

    private static final Map<CriterionType, List<Criterion>> mapCriterions = new HashMap<CriterionType, List<Criterion>>();

    private static Map<OrderElementTemplate, String> templateElementsAndCodes;

    @Autowired
    private IOrderElementDAO orderElementDAO;

    @Autowired
    private IOrderDAO orderDAO;

    @Autowired
    private IOrderElementTemplateDAO dao;

    @Autowired
    private ILabelDAO labelDAO;

    @Autowired
    private IQualityFormDAO qualityFormDAO;

    @Autowired
    private ICriterionTypeDAO criterionTypeDAO;

    @Autowired
    private ICriterionDAO criterionDAO;

    @Autowired
    private IAdHocTransactionService transaction;

    @Autowired
    private IScenarioManager scenarioManager;

    private OrderElementTemplate template;

    private TemplatesTree treeModel;

    private LabelsOnConversation labelsOnConversation;

    @Autowired
    private PlanningStateCreator planningStateCreator;

    private PlanningState planningState;

    private LabelsOnConversation getLabelsOnConversation() {
        if (labelsOnConversation == null) {
            labelsOnConversation = new LabelsOnConversation(labelDAO);
        }
        return labelsOnConversation;
    }

    private QualityFormsOnConversation qualityFormsOnConversation;

    private QualityFormsOnConversation getQualityFormsOnConversation() {
        if (qualityFormsOnConversation == null) {
            qualityFormsOnConversation = new QualityFormsOnConversation(
                    qualityFormDAO);
        }
        return qualityFormsOnConversation;
    }

    private OrderElementsOnConversation orderElementsOnConversation;

    public OrderElementsOnConversation getOrderElementsOnConversation() {
        if (orderElementsOnConversation == null) {
            orderElementsOnConversation = new OrderElementsOnConversation(
                    orderElementDAO, orderDAO);
        }
        return orderElementsOnConversation;
    }

    @Override
    public List<? extends OrderElementTemplate> getRootTemplates() {
        return dao.getRootBudgetTemplates();
    }

    @Override
    public OrderElementTemplate getTemplate() {
        if (template != null) {
            return template;
        } else if (planningState != null) {
            return planningState.getOrder().getAssociatedBudgetObject();
        }
        return null;
    }

    @Override
    public void confirmSave() {
        transaction.runOnTransaction(new IOnTransaction<Void>() {
            @Override
            public Void execute() {
                dao.save(template);
                return null;
            }
        });
        dontPoseAsTransient(template);
    }

    private void dontPoseAsTransient(OrderElementTemplate template) {
        template.dontPoseAsTransientObjectAnymore();
        List<OrderElementTemplate> childrenTemplates = template
                .getChildrenTemplates();
        for (OrderElementTemplate each : childrenTemplates) {
            dontPoseAsTransient(each);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void createTemplateFrom(OrderElement orderElement) {
        initializeAcompanyingObjectsOnConversation();
        Order order = orderDAO.loadOrderAvoidingProxyFor(orderElement);
        order.useSchedulingDataFor(getCurrentScenario());
        OrderElement orderElementOrigin = orderElementDAO
                .findExistingEntity(orderElement
                .getId());
        template = orderElementOrigin.createTemplate();
        loadAssociatedData(template);
        treeModel = new TemplatesTree(template);
    }

    public Scenario getCurrentScenario() {
        return scenarioManager.getCurrent();
    }

    @Override
    @Transactional(readOnly = true)
    public void initEdit(OrderElementTemplate template) {
        initializeAcompanyingObjectsOnConversation();
        dao.reattach(template);
        this.template = template;
        loadAssociatedData(this.template);
        treeModel = new TemplatesTree(this.template);
    }

    @Override
    @Transactional(readOnly = true)
    public void initEdit(Order order, Desktop desktop) {
        initializeAcompanyingObjectsOnConversation();
        this.planningState = planningStateCreator.retrieveOrCreate(desktop,
                order, new IActionsOnRetrieval() {

                    @Override
                    public void onRetrieval(PlanningState planningState) {
                        planningState.reattach();
                    }
                });
        loadAssociatedData(getTemplate());
        treeModel = new TemplatesTree(getTemplate());
    }

    @Override
    @Transactional(readOnly = true)
    public void initCreate() {
        initializeAcompanyingObjectsOnConversation();
        BudgetTemplate template = BudgetTemplate.create();
        this.template = template;
        treeModel = new TemplatesTree(this.template);
    }

    private void loadAssociatedData(OrderElementTemplate template) {
        setupTemplatesMap(template);
        loadAdvanceAssignments(template);
        loadQualityForms(template);
        loadLabels(template);
        loadCriterionRequirements(template);
        getOrderElementsOnConversation().initialize(template);
    }

    @Transactional(readOnly = true)
    private void setupTemplatesMap(OrderElementTemplate root) {
        templateElementsAndCodes.put(root, root.getCode());
        for(OrderElementTemplate child : root.getAllChildren()) {
            templateElementsAndCodes.put(child, child.getCode());
        }
    }

    private static void loadCriterionRequirements(OrderElementTemplate orderElement) {
        orderElement.getHoursGroups().size();
        for (HoursGroup hoursGroup : orderElement.getHoursGroups()) {
            attachDirectCriterionRequirement(hoursGroup
                    .getDirectCriterionRequirement());
        }
        attachDirectCriterionRequirement(orderElement
                .getDirectCriterionRequirements());

        for (OrderElementTemplate child : orderElement.getChildren()) {
            loadCriterionRequirements(child);
        }
    }

    private static void attachDirectCriterionRequirement(
            Set<DirectCriterionRequirement> requirements) {
        for (DirectCriterionRequirement requirement : requirements) {
            requirement.getChildren().size();
            requirement.getCriterion().getName();
            requirement.getCriterion().getType().getName();
        }
    }

    private void loadQualityForms(OrderElementTemplate template) {
        for (QualityForm each : template.getQualityForms()) {
            each.getName();
        }
        for (OrderElementTemplate each : template.getChildrenTemplates()) {
            loadQualityForms(each);
        }
    }

    private void loadLabels(OrderElementTemplate template) {
        for (Label each : template.getLabels()) {
            each.getName();
        }
        for (OrderElementTemplate each : template.getChildrenTemplates()) {
            loadLabels(each);
        }

    }

    private void loadAdvanceAssignments(OrderElementTemplate template) {
        for (AdvanceAssignmentTemplate each : template
                .getAdvanceAssignmentTemplates()) {
            each.getMaxValue();
            each.getAdvanceType().getUnitName();
        }
        for (OrderElementTemplate each : template.getChildrenTemplates()) {
            loadAdvanceAssignments(each);
        }
    }

    private void initializeAcompanyingObjectsOnConversation() {
        loadCriterions();
        getLabelsOnConversation().initializeLabels();
        getQualityFormsOnConversation().initialize();
        templateElementsAndCodes = new HashMap<OrderElementTemplate, String>();
    }

    private void loadCriterions() {
        mapCriterions.clear();
        List<CriterionType> criterionTypes = criterionTypeDAO
                .getCriterionTypes();
        for (CriterionType criterionType : criterionTypes) {
            List<Criterion> criterions = new ArrayList<Criterion>(criterionDAO
                    .findByType(criterionType));

            mapCriterions.put(criterionType, criterions);
        }
    }

    @Override
    public TemplatesTree getTemplatesTreeModel() {
        return treeModel;
    }

    @Override
    public boolean isTemplateTreeDisabled() {
        return template != null && template.isLeaf();
    }

    @Override
    public void addLabelToConversation(Label label) {
        getLabelsOnConversation().addLabel(label);
    }

    @Override
    public List<Label> getLabels() {
        return getLabelsOnConversation().getLabels();
    }

    @Override
    public Set<QualityForm> getAllQualityForms() {
        return new HashSet<QualityForm>(getQualityFormsOnConversation()
                .getQualityForms());
    }

    @Transactional(readOnly = true)
    public void validateTemplateName(String name)
            throws IllegalArgumentException {
        if ((name == null) || (name.isEmpty())) {
            throw new IllegalArgumentException(_("the name must be not empty"));
        }

        getTemplate().setName(name);
        if (!getTemplate().checkConstraintUniqueRootTemplateName()) {
            throw new IllegalArgumentException(
                    _("There exists other template with the same name."));
        }
    }

    @Override
    public List<Criterion> getCriterionsFor(CriterionType criterionType) {
        return mapCriterions.get(criterionType);
    }

    @Override
    public Map<CriterionType, List<Criterion>> getMapCriterions() {
        final Map<CriterionType, List<Criterion>> result =
            new HashMap<CriterionType, List<Criterion>>();
        result.putAll(mapCriterions);
        return result;
    }

    @Override
    @Transactional
    public void confirmDelete(OrderElementTemplate template) {
        try {
            dao.remove(template.getId());
        } catch (InstanceNotFoundException e) {
            throw new RuntimeException();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasNotApplications(OrderElementTemplate template) {
        getOrderElementsOnConversation().initialize(template);
        return getOrderElementsOnConversation().getOrderElements().isEmpty();
    }

    @Override
    public void validateTemplateCode(String code)
            throws IllegalArgumentException {
        if ((code == null) || (code.isEmpty())) {
            throw new IllegalArgumentException(_("the code must not be empty"));
        }
        // TODO complete with unique validation
    }

    @Override
    public void notifyUpdate(OrderElementTemplate element) {
        templateElementsAndCodes.put(element, element.getCode());
    }

    @Override
    public boolean checkValidCode(OrderElementTemplate element, String code) {
        for(Entry<OrderElementTemplate, String> entry : templateElementsAndCodes.entrySet()) {
            if(entry.getValue().equals(code) && !entry.getKey().equals(element)) {
                return false;
            }
        }
        return true;
    }

    @Override
    @Transactional
    public void saveThroughPlanningState(boolean showSaveMessage) {
        if (showSaveMessage) {
            this.planningState.getSaveCommand().save(null);
        } else {
            this.planningState.getSaveCommand().save(null, null);
        }
    }

    @Override
    @Transactional
    public void closeBudget() {
        Order order = getAssociatedOrder();
        Budget budget = order.getAssociatedBudgetObject();
        if (!order.getState().equals(OrderStatusEnum.BUDGET)) {
            throw new ValidationException(_("The budget is already closed"));
        }
        orderDAO.reattach(order);
        order.setState(OrderStatusEnum.OFFERED);
        budget.createOrderLineElementsForAssociatedOrder(scenarioManager
                .getCurrent());
    }

    @Override
    public Order getAssociatedOrder() {
        return planningState.getOrder();
    }

    @Override
    public boolean isReadOnly() {
        return !getAssociatedOrder().getState().equals(OrderStatusEnum.BUDGET);
    }
}