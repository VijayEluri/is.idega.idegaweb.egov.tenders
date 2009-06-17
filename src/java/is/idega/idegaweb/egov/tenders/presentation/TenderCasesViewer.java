package is.idega.idegaweb.egov.tenders.presentation;

import is.idega.idegaweb.egov.cases.presentation.OpenCases;
import is.idega.idegaweb.egov.tenders.TendersConstants;
import is.idega.idegaweb.egov.tenders.business.TendersHelper;
import java.util.Collection;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;

import com.idega.block.process.presentation.UserCases;
import com.idega.block.process.presentation.beans.CasePresentation;
import com.idega.builder.business.BuilderLogic;
import com.idega.presentation.IWContext;
import com.idega.presentation.Layer;
import com.idega.presentation.paging.PagedDataCollection;
import com.idega.presentation.text.Heading1;
import com.idega.presentation.text.Link;
import com.idega.presentation.text.ListItem;
import com.idega.presentation.text.Lists;
import com.idega.user.data.User;
import com.idega.util.CoreConstants;
import com.idega.util.ListUtil;
import com.idega.util.PresentationUtil;
import com.idega.util.URIUtil;
import com.idega.util.expression.ELUtil;

/**
 * Viewer filters tenders cases
 * 
 * @author <a href="mailto:valdas@idega.com">Valdas Žemaitis</a>
 * @version $Revision: 1.7 $
 *
 * Last modified: $Date: 2009/06/17 12:35:54 $ by: $Author: valdas $
 */
public class TenderCasesViewer extends OpenCases {
	
	@Autowired
	private TendersHelper tendersHelper;
	
	@Override
	protected void present(IWContext iwc) throws Exception {
		ELUtil.getInstance().autowire(this);
	}
	
	@Override
	protected void display(IWContext iwc) throws Exception {
		PresentationUtil.addStyleSheetToHeader(iwc, getBundle().getVirtualPathWithFileNameString("style/tenders.css"));
		
		if (parseAction(iwc) == ACTION_VIEW) {
			User currentUser = iwc.isLoggedOn() ? iwc.getCurrentUser() : null;
			
			Layer container = new Layer();
			add(container);
			container.setStyleClass("currentTendersCasesViewer");
			
			PagedDataCollection<CasePresentation> cases = tendersHelper.getAllCases(iwc.getCurrentLocale(), getCaseStatusesToHide(), getCaseStatusesToShow());
			if (cases == null || ListUtil.isEmpty(cases.getCollection())) {
				container.add(new Heading1(getResourceBundle(iwc).getLocalizedString("tender_cases.no_cases_available", "There are no cases currently")));
				return;
			}
			
			Collection<CasePresentation> validCases = tendersHelper.getValidTendersCases(cases.getCollection(), currentUser, iwc.getCurrentLocale());
			if (ListUtil.isEmpty(validCases)) {
				container.add(new Heading1(getResourceBundle(iwc).getLocalizedString("tender_cases.no_cases_available", "There are no cases currently")));
				return;
			}
			
			Lists list = new Lists();
			container.add(list);
			for (CasePresentation theCase: validCases) {
				ListItem item = new ListItem();
				list.add(item);
				
				item.add(getLinkToCase(iwc, theCase, currentUser));
			}
			
			return;
		}
		
		super.display(iwc);
	}
	
	private Link getLinkToCase(IWContext iwc, CasePresentation theCase, User currentUser) {
		Link link = new Link(theCase.getSubject());
		
		if (tendersHelper.isSubscribed(iwc, currentUser, theCase.getId())) {
			//	Link to subscribed case
			link.setURL(tendersHelper.getLinkToSubscribedCase(iwc, currentUser, theCase.getId()));
		} else {
			//	Link to tender case viewer AND possibility to subscribe to case
			link.setURL(getUri(iwc, TendersConstants.PAGE_TYPE_CASE_VIEWER, theCase.getId()));
		}
		
		return link;
	}
	
	private String getUri(IWContext iwc, String pageType, String caseId) {
		return getUri(iwc, pageType, caseId, null);
	}

	private String getUri(IWContext iwc, String pageType, String caseId, Integer viewType) {
		String url = null;
		try {
			url = BuilderLogic.getInstance().getFullPageUrlByPageType(iwc, pageType, true);
		} catch(Exception e) {}
		if (url == null) {
			Logger.getLogger(getClass().getName()).warning("Didn't find page by type: " + pageType);
			return CoreConstants.EMPTY;
		}
		
		URIUtil uriUtil = new URIUtil(url);
		uriUtil.setParameter(PARAMETER_CASE_PK, caseId);
		if (viewType != null) {
			uriUtil.setParameter(UserCases.PARAMETER_ACTION, String.valueOf(viewType));
		}
		
		return uriUtil.getUri();
	}
	
	@Override
	public String getBundleIdentifier() {
		return TendersConstants.IW_BUNDLE_IDENTIFIER;
	}
}
