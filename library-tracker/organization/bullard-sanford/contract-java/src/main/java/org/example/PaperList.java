/*
SPDX-License-Identifier: Apache-2.0
*/

package org.example;

import org.example.ledgerapi.StateList;
import org.hyperledger.fabric.contract.Context;

public class PaperList {

    private StateList stateList;

    public PaperList(Context ctx) {
        this.stateList = StateList.getStateList(ctx, PaperList.class.getSimpleName(), CommercialPaper::deserialize);
    }

    public PaperList addPaper(CommercialPaper paper) {
        stateList.addState(paper);
        return this;
    }

    public CommercialPaper getPaper(String paperKey) {
        return (CommercialPaper) this.stateList.getState(paperKey);
    }

    public PaperList updatePaper(CommercialPaper paper) {
        this.stateList.updateState(paper);
        return this;
    }
}
