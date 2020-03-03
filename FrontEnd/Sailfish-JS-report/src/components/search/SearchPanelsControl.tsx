/*******************************************************************************
 * Copyright 2009-2020 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 *  limitations under the License.
 ******************************************************************************/

import * as React from 'react';
import { connect } from "react-redux";
import AppState from "../../state/models/AppState";
import { setSearchLeftPanelEnabled, setSearchRightPanelEnabled } from "../../actions/actionCreators";
import Panel from "../../util/Panel";
import PanelSide from "../../util/PanelSide";
import { createBemElement } from "../../helpers/styleCreators";

interface StateProps {
    leftPanelEnabled: boolean;
    leftPanel: Panel;
    rightPanelEnabled: boolean;
    rightPanel: Panel;
    closedPanel: PanelSide | null;
}

interface DispatchProps {
    onLeftPanelEnable: (isEnabled: boolean) => void;
    onRightPanelEnable: (isEnabled: boolean) => void;
}

interface Props extends StateProps, DispatchProps {}

function SearchPanelControlBase({ leftPanelEnabled, rightPanelEnabled, onRightPanelEnable, onLeftPanelEnable, leftPanel, rightPanel, closedPanel }: Props) {
    const onLeftPanelSelect = () => {
        if (closedPanel != PanelSide.LEFT) {
            onLeftPanelEnable(!leftPanelEnabled);
        }
    };

    const onRightPanelSelect = () => {
        if (closedPanel != PanelSide.RIGHT) {
            onRightPanelEnable(!rightPanelEnabled);
        }
    };

    const leftControlClassName = createBemElement(
        'search-panel-controls',
        'checkbox-wrapper',
        closedPanel == PanelSide.LEFT ? 'disabled' : null
    );
    const rightControlClassName = createBemElement(
        'search-panel-controls',
        'checkbox-wrapper',
        closedPanel == PanelSide.RIGHT ? 'disabled' : null
    );

    return (
        <div className="search-panel-controls">
            <div className={leftControlClassName}>
                <input
                    className="search-panel-controls__checkbox"
                    type="checkbox"
                    id="left-panel"
                    checked={leftPanelEnabled}
                    onChange={onLeftPanelSelect}/>
                <label htmlFor="left-panel" className="search-panel-controls__label">
                    {leftPanel.toLowerCase().replace('_', ' ')}
                </label>
            </div>
            <div className={rightControlClassName}>
                <input
                    className="search-panel-controls__checkbox"
                    type="checkbox"
                    id="right-panel"
                    checked={rightPanelEnabled}
                    onChange={onRightPanelSelect}/>
                <label htmlFor="right-panel" className="search-panel-controls__label">
                    {rightPanel.toLowerCase().replace('_', ' ')}
                </label>
            </div>
        </div>
    )
}

const SearchPanelControl = connect(
    (state: AppState): StateProps => ({
        leftPanelEnabled: state.selected.search.leftPanelEnabled,
        leftPanel: state.view.leftPanel,
        rightPanelEnabled: state.selected.search.rightPanelEnabled,
        rightPanel: state.view.rightPanel,
        closedPanel: state.view.closedPanelSide
    }),
    (dispatch): DispatchProps => ({
        onLeftPanelEnable: isEnabled => dispatch(setSearchLeftPanelEnabled(isEnabled)),
        onRightPanelEnable: isEnabled => dispatch(setSearchRightPanelEnabled(isEnabled))
    })
)(SearchPanelControlBase);

export default SearchPanelControl;
