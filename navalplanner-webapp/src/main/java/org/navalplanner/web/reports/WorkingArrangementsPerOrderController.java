/*
 * This file is part of NavalPlan
 *
 * Copyright (C) 2009-2010 Fundación para o Fomento da Calidade Industrial e
 *                         Desenvolvemento Tecnolóxico de Galicia
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

package org.navalplanner.web.reports;

import static org.navalplanner.web.I18nHelper._;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.jasperreports.engine.JRDataSource;

import org.navalplanner.business.orders.entities.Order;
import org.navalplanner.business.planner.entities.TaskStatusEnum;
import org.navalplanner.web.common.components.ExtendedJasperreport;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.WrongValueException;
import org.zkoss.zul.Checkbox;
import org.zkoss.zul.Listbox;
import org.zkoss.zul.Listitem;

/**
 *
 * @author Diego Pino Garcia <dpino@igalia.com>
 *
 */
public class WorkingArrangementsPerOrderController extends NavalplannerReportController {

    private static final String REPORT_NAME = "workingArrangementsPerOrderReport";

    private IWorkingArrangementsPerOrderModel workingArrangementsPerOrderModel;

    private Listbox lbOrders;

    private Listbox lbTaskStatus;

    private Checkbox cbShowDependencies;

    @Override
    public void doAfterCompose(Component comp) throws Exception {
        super.doAfterCompose(comp);
        comp.setVariable("controller", this, true);
    }

    public List<Order> getOrders() {
        return workingArrangementsPerOrderModel.getOrders();
    }

    protected String getReportName() {
        return REPORT_NAME;
    }

    protected JRDataSource getDataSource() {
        return workingArrangementsPerOrderModel
                .getWorkingArrangementsPerOrderReportReport(getSelectedOrder(),
                        getSelectedTaskStatus(), showDependencies());
    }

    private boolean showDependencies() {
        return cbShowDependencies.isChecked();
    }

    private TaskStatusEnum getSelectedTaskStatus() {
        final Listitem item = lbTaskStatus.getSelectedItem();
        return (item != null) ? (TaskStatusEnum) item.getValue() : TaskStatusEnum.ALL;
    }

    private Order getSelectedOrder() {
        final Listitem item = lbOrders.getSelectedItem();
        return  (item != null) ? (Order) item.getValue() : null;
    }

    protected Map<String, Object> getParameters() {
        Map<String, Object> result = new HashMap<String, Object>();

        result.put("orderName", getSelectedOrder().getName());

        // Task status
        final TaskStatusEnum taskStatus = getSelectedTaskStatus();
        result.put("taskStatus", taskStatus.toString());

        return result;
    }

    public void showReport(ExtendedJasperreport jasperreport) {
        if (getSelectedOrder() == null) {
            throw new WrongValueException(lbOrders, _("Please, select an order"));
        }
        super.showReport(jasperreport);
    }

    public List<TaskStatusEnum> getTasksStatus() {
        List<TaskStatusEnum> result = new ArrayList<TaskStatusEnum>();
        result.addAll(Arrays.asList(TaskStatusEnum.values()));
        Collections.sort(result, new TaskStatusEnumComparator());
        return result;
    }

    private class TaskStatusEnumComparator implements Comparator<TaskStatusEnum> {

        @Override
        public int compare(TaskStatusEnum arg0, TaskStatusEnum arg1) {
            return arg0.toString().compareTo(arg1.toString());
        }

    }

}
