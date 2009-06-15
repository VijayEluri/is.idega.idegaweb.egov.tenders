package is.idega.idegaweb.egov.tenders.business.impl;

import is.idega.idegaweb.egov.tenders.TendersConstants;
import is.idega.idegaweb.egov.tenders.bean.CasePresentationInfo;
import is.idega.idegaweb.egov.tenders.business.TendersHelper;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jbpm.context.exe.VariableInstance;
import org.jbpm.context.exe.variableinstance.DateInstance;
import org.jbpm.context.exe.variableinstance.HibernateStringInstance;
import org.jbpm.context.exe.variableinstance.StringInstance;
import org.jbpm.graph.exe.ProcessInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.idega.block.process.business.CaseBusiness;
import com.idega.block.process.business.CaseManagersProvider;
import com.idega.block.process.data.Case;
import com.idega.block.process.data.CaseCode;
import com.idega.block.process.data.CaseCodeHome;
import com.idega.block.process.data.CaseHome;
import com.idega.block.process.presentation.beans.CasePresentation;
import com.idega.builder.business.BuilderLogicWrapper;
import com.idega.business.IBOLookup;
import com.idega.business.IBOLookupException;
import com.idega.core.accesscontrol.business.AccessController;
import com.idega.core.accesscontrol.business.LoginSession;
import com.idega.data.IDOLookup;
import com.idega.data.IDOLookupException;
import com.idega.idegaweb.IWApplicationContext;
import com.idega.idegaweb.IWMainApplication;
import com.idega.idegaweb.egov.bpm.data.CaseProcInstBind;
import com.idega.idegaweb.egov.bpm.data.dao.CasesBPMDAO;
import com.idega.jbpm.exe.BPMFactory;
import com.idega.jbpm.exe.TaskInstanceW;
import com.idega.jbpm.identity.BPMUser;
import com.idega.jbpm.identity.Role;
import com.idega.jbpm.identity.permission.Access;
import com.idega.jbpm.variables.BinaryVariable;
import com.idega.presentation.IWContext;
import com.idega.presentation.paging.PagedDataCollection;
import com.idega.user.data.User;
import com.idega.util.CoreConstants;
import com.idega.util.ListUtil;
import com.idega.util.StringUtil;
import com.idega.util.expression.ELUtil;

/**
 * Helper methods for tenders project logic
 * @author <a href="mailto:valdas@idega.com">Valdas Žemaitis</a>
 * @version $Revision: 1.1 $
 *
 * Last modified: $Date: 2009/06/10 07:05:00 $ by: $Author: valdas $
 */
@Service
@Scope(BeanDefinition.SCOPE_SINGLETON)
public class TendersHelperImp implements TendersHelper {

	private static final Logger LOGGER = Logger.getLogger(TendersHelperImp.class.getName());
	
	@Autowired
	private CaseManagersProvider caseManagersProvider;
	
	@Autowired
	private CasesBPMDAO casesDAO;
	
	@Autowired
	private BuilderLogicWrapper builderLorgicWrapper;
	
	@Autowired
	private BPMFactory bpmFactory;
	
	public PagedDataCollection<CasePresentation> getAllCases(Locale locale, String statusesToHide, String statusesToShow) {
		CaseHome caseHome = null;
		try {
			caseHome = (CaseHome) IDOLookup.getHome(Case.class);
		} catch (IDOLookupException e) {
			LOGGER.log(Level.WARNING, "Error getting " + CaseHome.class, e);
		}
		if (caseHome == null) {
			return null;
		}
		CaseCodeHome caseCodeHome = null;
		try {
			caseCodeHome = (CaseCodeHome) IDOLookup.getHome(CaseCode.class);
		} catch(Exception e) {
			LOGGER.log(Level.WARNING, "Error getting " + CaseCodeHome.class, e);
		}
		if (caseCodeHome == null) {
			return null;
		}
		
		Collection<Case> cases = null;
		try {
			cases = caseHome.findAllByCaseCode(caseCodeHome.findByPrimaryKey(TendersConstants.TENDER_CASES_CODE));
		} catch(Exception e) {
			LOGGER.log(Level.SEVERE, "Error getting tenders cases", e);
		}
		if (ListUtil.isEmpty(cases)) {
			return null;
		}
		
		Collection<Case> filteredCases = null;
		List<String> allStatusesToShow = StringUtil.getValuesFromString(statusesToShow, CoreConstants.COMMA);
		List<String> allStatusesToHide = StringUtil.getValuesFromString(statusesToHide, CoreConstants.COMMA);
		if (allStatusesToShow != null || allStatusesToHide != null) {
			filteredCases = new ArrayList<Case>();
			for (Case theCase: cases) {
				String status = theCase.getStatus();
				
				if (allStatusesToShow != null && allStatusesToShow.contains(status)) {
					filteredCases.add(theCase);						//	Current status is defined to SHOW case
				} else if (ListUtil.isEmpty(allStatusesToHide)) {
					filteredCases.add(theCase);						//	ALL cases with statuses must be SHOWN
				} else if (!allStatusesToHide.contains(status)) {
					filteredCases.add(theCase);						//	Current status is NOT defined to HIDE 
				}
			}
			if (ListUtil.isEmpty(filteredCases)) {
				return null;
			}
		}
		
		return getCaseManagersProvider().getCaseManager().getCasesByEntities(filteredCases == null ? cases : filteredCases, locale);
	}
	
	@Transactional(readOnly=true)
	public Collection<CasePresentation> getValidTendersCases(Collection<CasePresentation> cases, User currentUser) {
		if (ListUtil.isEmpty(cases)) {
			return null;
		}
		
		Collection<String> identifiers = new ArrayList<String>();
		for (CasePresentation theCase: cases) {
			if (theCase.isBpm() && !identifiers.contains(theCase.getCaseIdentifier())) {
				identifiers.add(theCase.getCaseIdentifier());
			}
		}
		if (ListUtil.isEmpty(identifiers)) {
			return null;
		}
		
		List<Object[]> binds = null;
		try {
			binds = getCasesDAO().getCaseProcInstBindProcessInstanceByCaseIdentifier(identifiers);
		} catch(Exception e) {
			LOGGER.log(Level.SEVERE, "Error getting binds for cases identifiers: " + identifiers, e);
		}
		if (ListUtil.isEmpty(binds)) {
			return null;
		}
		
		Map<Long, CasePresentationInfo> info = new HashMap<Long, CasePresentationInfo>();
		
		Collection<Long> processInstanceIds = new ArrayList<Long>();
		for (Object[] bind: binds) {
			Object o = bind[1];
			if (o instanceof ProcessInstance) {
				Long id = ((ProcessInstance) o).getId();
				if (!processInstanceIds.contains(id)) {
					o = bind[0];
					if (o instanceof CaseProcInstBind) {
						CasePresentationInfo caseInfo = getCaseInfo(cases, ((CaseProcInstBind) o).getCaseId(), id);
						if (caseInfo != null) {
							processInstanceIds.add(id);
							info.put(id, caseInfo);
						}
					}
				}
			}
		}
		if (ListUtil.isEmpty(processInstanceIds)) {
			LOGGER.warning("No process instances found for identifiers: " + identifiers);
			return null;
		}
		
		List<VariableInstance> variables = null;
		try {
			variables = getCasesDAO().getVariablesByProcessInstanceIdAndVariablesNames(processInstanceIds, Arrays.asList(
					TendersConstants.TENDER_CASE_START_DATE_VARIABLE,
					TendersConstants.TENDER_CASE_END_DATE_VARIABLE,
					TendersConstants.TENDER_CASE_IS_PRIVATE_VARIABLE,
					TendersConstants.TENDER_CASE_IS_PAYMENT_VARIABLE
			));
		} catch(Exception e) {
			LOGGER.log(Level.SEVERE, "Error getting date variables for processes: " + processInstanceIds, e);
		}
		if (ListUtil.isEmpty(variables)) {
			return null;
		}
		
		for (VariableInstance variable: variables) {
			if (variable instanceof DateInstance) {
				Object o = variable.getValue();
				if (o instanceof Timestamp) {
					CasePresentationInfo caseInfo = info.get(variable.getProcessInstance().getId());
					if (caseInfo != null) {
						String name = variable.getName();
						if (TendersConstants.TENDER_CASE_START_DATE_VARIABLE.equals(name)) {
							caseInfo.setStartDate((Timestamp) o);
						} else if (TendersConstants.TENDER_CASE_END_DATE_VARIABLE.equals(name)) {
							caseInfo.setEndDate((Timestamp) o);
						}
					}
				}
			} else if (variable instanceof StringInstance || variable instanceof HibernateStringInstance) {
				CasePresentationInfo caseInfo = info.get(variable.getProcessInstance().getId());
				if (caseInfo != null) {
					if (TendersConstants.TENDER_CASE_IS_PRIVATE_VARIABLE.equals(variable.getName())) {
						caseInfo.setCaseIsPrivate(Boolean.valueOf(variable.getValue().toString()));
					} else if (TendersConstants.TENDER_CASE_IS_PAYMENT_VARIABLE.equals(variable.getName())) {
						caseInfo.setPaymentCase(Boolean.valueOf(variable.getValue().toString()));
					}
				}
			}
		}
		
		Timestamp currentTime = new Timestamp(System.currentTimeMillis());
		Collection<CasePresentation> validCases = new ArrayList<CasePresentation>();
		for (CasePresentationInfo caseInfo: info.values()) {
			if (isCaseVisible(caseInfo, currentUser)) {
				if (caseInfo.getStartDate() != null && caseInfo.getEndDate() != null) {
					if (currentTime.after(caseInfo.getStartDate()) && currentTime.before(caseInfo.getEndDate())) {
						validCases.add(caseInfo.getCasePresentation());
					}
				}
			}
		}
		return validCases;
	}
	
	private boolean isCaseVisible(CasePresentationInfo caseInfo, User currentUser) {
		if (!caseInfo.isCaseIsPrivate()) {
			return true;
		}
		
		caseInfo.getCasePresentation().setPrivate(true);
		
		if (currentUser == null) {
			return false;
		}
		
		AccessController accessController = IWMainApplication.getDefaultIWMainApplication().getAccessController();
		if (canManageCaseSubscribers(currentUser)) {
			return true;									//	Handler can see all cases
		} else if (accessController.hasRole(currentUser, TendersConstants.TENDER_CASES_OWNER_ROLE)) {
			return isUserOwner(caseInfo, currentUser);		//	Owner can see case
		} else if (isUserInvited(caseInfo, currentUser)) {
			return isUserInvited(caseInfo, currentUser);	//	User can see case if invited
		}
		
		return false;
	}
	
	public boolean canManageCaseSubscribers(User user) {
		if (user == null) {
			return false;
		}
		
		try {
			LoginSession loginSession = ELUtil.getInstance().getBean(LoginSession.class);
			if (loginSession != null && loginSession.isSuperAdmin()) {
				return true;
			}
		} catch(Exception e) {}
		
		return IWMainApplication.getDefaultIWMainApplication().getAccessController().hasRole(user, TendersConstants.TENDER_CASES_HANDLER_ROLE);
	}
	
	private boolean isUserInvited(CasePresentationInfo caseInfo, User currentUser) {
		return isSubscribed(IWMainApplication.getDefaultIWApplicationContext(), currentUser, caseInfo.getCasePresentation().getId());
	}
	
	private boolean isUserOwner(CasePresentationInfo caseInfo, User currentUser) {
		User owner = caseInfo.getCasePresentation().getOwner();
		if (owner == null) {
			return false;
		}
		
		return owner.getId().equals(currentUser.getId());
	}
	
	private CasePresentationInfo getCaseInfo(Collection<CasePresentation> cases, Integer caseId, Long processInstanceId) {
		String id = String.valueOf(caseId);
		for (CasePresentation theCase: cases) {
			if (id.equals(theCase.getId())) {
				return new CasePresentationInfo(processInstanceId, theCase);
			}
		}
		
		return null;
	}

	public CasesBPMDAO getCasesDAO() {
		return casesDAO;
	}

	public void setCasesDAO(CasesBPMDAO casesDAO) {
		this.casesDAO = casesDAO;
	}

	public CaseManagersProvider getCaseManagersProvider() {
		return caseManagersProvider;
	}

	public void setCaseManagersProvider(CaseManagersProvider caseManagersProvider) {
		this.caseManagersProvider = caseManagersProvider;
	}

	@Transactional(readOnly=true)
	public CasePresentationInfo getTenderCaseInfo(Object caseId) {
		if (caseId == null) {
			return null;
		}
		
		CaseProcInstBind bind = null;
		try {
			bind = getCasesDAO().getCaseProcInstBindByCaseId(Integer.valueOf(caseId.toString()));
		} catch(Exception e) {
			LOGGER.log(Level.WARNING, "Error getting BPM case by ID: " + caseId, e);
		}
		if (bind == null) {
			return null;
		}
		
		CasePresentationInfo info = new CasePresentationInfo(bind.getProcInstId());
		
		List<VariableInstance> variables = null;
		try {
			variables = getCasesDAO().getVariablesByProcessInstanceIdAndVariablesNames(Arrays.asList(info.getProcessInstanceId()), Arrays.asList(
					TendersConstants.TENDER_CASE_TENDER_NAME_VARIABLE,
					TendersConstants.TENDER_CASE_TENDER_ISSUER_VARIABLE,
					TendersConstants.TENDER_CASE_JOB_DESCRIPTION_VARIABLE,
					TendersConstants.TENDER_CASE_IS_PRIVATE_VARIABLE,
					TendersConstants.TENDER_CASE_IS_PAYMENT_VARIABLE
			));
		} catch(Exception e) {
			LOGGER.log(Level.WARNING, "Error getting info about tender case: " + caseId, e);
		}
		if (ListUtil.isEmpty(variables)) {
			return info;
		}
		
		for (VariableInstance variable: variables) {
			if (variable instanceof StringInstance || variable instanceof HibernateStringInstance) {
				info.addInfo(variable.getName(), variable.getValue().toString());
				
				if (TendersConstants.TENDER_CASE_IS_PRIVATE_VARIABLE.equals(variable.getName())) {
					info.setCaseIsPrivate(Boolean.valueOf(variable.getValue().toString()));
				} else if (TendersConstants.TENDER_CASE_IS_PAYMENT_VARIABLE.equals(variable.getName())) {
					info.setPaymentCase(Boolean.valueOf(variable.getValue().toString()));
				}
			}
		}
		
		return info;
	}

	public boolean isSubscribed(IWApplicationContext iwac, User user, String caseId) {
		if (user == null || caseId == null) {
			return false;
		}
		
		CaseBusiness caseBusiness = getCaseBusiness(iwac);
		if (caseBusiness == null) {
			return false;
		}
		
		return caseBusiness.isSubscribed(caseId, user);
	}
	
	public String getLinkToSubscribedCase(IWContext iwc, User user, String caseId) {
		CaseProcInstBind bind = null;
		try {
			bind = getCasesDAO().getCaseProcInstBindByCaseId(Integer.valueOf(caseId));
		} catch(Exception e) {
			LOGGER.log(Level.WARNING, "Error getting bind for case: " + caseId, e);
		}
		if (bind == null) {
			return null;
		}
		
		return getLinkToSubscribedCase(iwc, user, bind.getProcInstId());
	}

	public String getLinkToSubscribedCase(IWContext iwc, User user, Long processInstanceId) {
		String uri = builderLorgicWrapper.getBuilderService(iwc).getFullPageUrlByPageType(user, iwc, BPMUser.defaultAssetsViewPageType, true);
		return uri + "?piId=" + processInstanceId;
	}

	public boolean doSubscribeToCase(IWContext iwc, User user, String caseId) {
		if (user == null || StringUtil.isEmpty(caseId)) {
			return false;
		}
		
		try {
			CaseBusiness caseBusiness = getCaseBusiness(iwc);
			if (caseBusiness == null) {
				return false;
			}
			
			return caseBusiness.addSubscriber(caseId, user);
		} catch(Exception e) {
			LOGGER.log(Level.WARNING, "Error subscribing to case: " + caseId + ", for user: " + user, e);
		}
		
		return false;
	}
	
	public CaseBusiness getCaseBusiness(IWApplicationContext iwac) {
		try {
			return IBOLookup.getServiceInstance(iwac == null ? IWMainApplication.getDefaultIWApplicationContext() : iwac, CaseBusiness.class);
		} catch (IBOLookupException e) {
			LOGGER.log(Level.WARNING, "Error getting: " + CaseBusiness.class, e);
		}
		return null;
	}

	public BPMFactory getBpmFactory() {
		return bpmFactory;
	}

	public void setBpmFactory(BPMFactory bpmFactory) {
		this.bpmFactory = bpmFactory;
	}

	public boolean disableToSeeAllAttachments(TaskInstanceW taskInstance) {
		for (String roleName: TendersConstants.TENDER_CASES_3RD_PARTIES_ROLES) {
			if (!setAccessRight(taskInstance, roleName, null)) {
				return false;
			}
		}
		
		return true;
	}

	public boolean enableToSeeAllAttachments(TaskInstanceW taskInstance) {
		for (String roleName: TendersConstants.TENDER_CASES_3RD_PARTIES_ROLES) {
			if (!setAccessRight(taskInstance, roleName, Access.read)) {
				return false;
			}
		}
		
		return true;
	}
	
	@Transactional(readOnly = true)
	private boolean setAccessRight(TaskInstanceW taskInstance, String roleName, Access access) {
		if (taskInstance == null) {
			return false;
		}
		
		List<BinaryVariable> attachments = taskInstance.getAttachments();
		if (ListUtil.isEmpty(attachments)) {
			return true;
		}
		
		try {
			for (BinaryVariable attachment: attachments) {
				taskInstance.setTaskRolePermissions(new Role(roleName, access), false, attachment.getIdentifier());
			}
		} catch(Exception e) {
			LOGGER.log(Level.WARNING, "Error setting access rights for task: " + taskInstance.getTaskInstanceId(), e);
			return false;
		}
		
		return true;
	}
	
}