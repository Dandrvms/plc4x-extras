/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.plc4x.malbec.s88.recipes.actions;

import java.awt.event.ActionEvent;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import org.openide.util.ContextAwareAction;
import org.openide.util.Lookup;

/**
 *
 * @author ceos
 */
public class NewRecipeAction extends AbstractAction implements ContextAwareAction{
    
    private final Lookup context;
    
    public NewRecipeAction(){
        this(Lookup.EMPTY);
    }
    
    private NewRecipeAction(Lookup context){
        super("New Recipe");
        this.context = context;
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
        System.out.println("New recipe placeholder");
    }

    @Override
    public Action createContextAwareInstance(Lookup lkp) {
        return new NewRecipeAction(lkp);
    }
    
}
