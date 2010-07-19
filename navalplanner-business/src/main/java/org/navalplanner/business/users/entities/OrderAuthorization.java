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

package org.navalplanner.business.users.entities;

import org.hibernate.validator.NotNull;
import org.navalplanner.business.common.BaseEntity;
import org.navalplanner.business.orders.entities.Order;

/**
 * Base entity for modeling a order authorization.
 *
 * @author Jacobo Aragunde Perez <jaragunde@igalia.com>
 */
public abstract class OrderAuthorization extends BaseEntity {

    private OrderAuthorizationType authorizationType;

    private Order order;

    public void setAuthorizationType(OrderAuthorizationType authorizationType) {
        this.authorizationType = authorizationType;
    }

    @NotNull(message="an authorization type must be set")
    public OrderAuthorizationType getAuthorizationType() {
        return authorizationType;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public Order getOrder() {
        return order;
    }

}
