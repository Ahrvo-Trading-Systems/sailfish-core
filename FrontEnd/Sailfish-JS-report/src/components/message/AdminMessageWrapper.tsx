/******************************************************************************
 * Copyright 2009-2019 Exactpro (Exactpro Systems Limited)
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
 * limitations under the License.
 ******************************************************************************/

import * as React from 'react';
import "../../styles/messages.scss";
import { MessageCardProps, MessageCardContainer, MessageCardBase } from "./MessageCard";
import Message from '../../models/Message';
import { MessageCardActionChips } from "./MessageCardActionChips";
import { createBemBlock } from '../../helpers/styleCreators';

interface WrapperProps extends MessageCardProps {
    isExpanded: boolean;
    expandHandler: (isExpanded: boolean) => any;
}

const AdminMessageWrapperBase = (props: WrapperProps) => {

    if (props.isExpanded) {

        const expandButtonClass = createBemBlock(
            "mc-expand-btn", 
            props.message.content.rejectReason != null ? "rejected" : null
        );

        return (
            <div style={{position: "relative"}}>
                <MessageCardBase {...props}/>
                <div className={expandButtonClass}>
                    <div className="mc-expand-btn__icon" onClick={() => props.expandHandler(!props.isExpanded)}/>
                </div>
            </div>
        );
    }

    const rootClass = createBemBlock(
        "message-card",
        props.status,
        props.isSelected ? "selected" : null
    );

    return (
        <div className={rootClass}
            onClick={() => props.selectHandler()}>
            <div className="message-card__labels">
                {renderMessageTypeLabels(props.message)}
            </div>
            <div className="message-card__header   mc-header small"
                data-lb-count={getLabelsCount(props.message)}>
                <div className="mc-header__info">
                    <MessageCardActionChips
                        message={props.message}/>
                </div>
                <div className="mc-header__name">Name</div>
                <div className="mc-header__name-value">{props.message.msgName}</div>
                <div className="mc-header__expand">
                    <div className="mc-header__expand-icon" onClick={() => props.expandHandler(!props.isExpanded)}/>
                </div>
            </div>
        </div>
    );
}

function renderMessageTypeLabels(message: Message): React.ReactNodeArray {
    let labels = [];

    if (message.content.rejectReason !== null) {
        labels.push(
            <div className="mc-label rejected" key="rejected">
                <div className="mc-label__icon rejected" style={{marginTop: "10px"}}/>
            </div>
        );
    }

    if (message.content.admin) {
        labels.push(
            <div className="mc-label admin" key="admin">
                <div className="mc-label__icon admin" style={{marginTop: "10px"}}/>
            </div>
        )
    }

    return labels;
}

function getLabelsCount(message: Message) {
    let count = 0;

    if (message.content.rejectReason != null) {
        count++;
    }

    if (message.content.admin) {
        count++;
    }

    return count;
}


export const AdminMessageWrapper = MessageCardContainer(AdminMessageWrapperBase);
