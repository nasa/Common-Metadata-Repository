/**
 * ELASTICSEARCH CONFIDENTIAL
 * _____________________________
 *
 *  [2014] Elasticsearch Incorporated All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of Elasticsearch Incorporated and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Elasticsearch Incorporated
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Elasticsearch Incorporated.
 */

define(["_","kb","exports"],function(e,t,s){function i(e,t,s){"undefined"==typeof s&&(s=e),(e||{}).__scope_link&&(e=n(e.__scope_link,s,t)),this._rules=e,this._mode=r,this._context=t,this.scopeRules=s}function l(e){return null==e||void 0==typeof e?"null":e.__any_of||e instanceof Array?"list":e.__one_of?l(e.__one_of[0]):"object"==typeof e?"object":"value"}function n(s,l,n){if(e.isFunction(s))return s(n,l);var o=s.split("."),r=o.shift(),u=l;if("GLOBAL"==r)u=t.getGlobalAutocompleteComponents();else if(r){if(u=t.getEndpointDescriptionByEndpoint(r),!u)throw"Failed to resolve linked scheme: "+r;if(u=u.data_autocomplete_rules,!u)throw"No autocomplete rules defined in linked scheme: "+r}var _=new i(u),h=[];e.each(o,function(e){h.push("{",e)}),_.walkTokenPath(h);var c=_.getRules();if(!c)throw"Failed to resolve rules by link: "+s;return c}var o=1,r=2,u=3;e.defaults(i.prototype,{walkByToken:function(e){var t;if(this._mode==o){if("{"==e||"["==e)return this._rules=null,this._mode=u,null;switch(t=this._rules[e]||this._rules["*"]||this._rules["{field}"]||this._rules["{type}"],t&&t.__scope_link&&(t=n(t.__scope_link,this.scopeRules,this._context)),l(t)){case"object":case"list":this._mode=r;break;default:this._mode=u}return this._rules=t,t}if(this._mode==r){var s=l(this._rules);if("{"==e)return"object"!=s?(this._mode=u,this._rules=null):(this._mode=o,this._rules);if("["==e){if(this._rules.__any_of)t=this._rules.__any_of;else{if(!(this._rules instanceof Array))return this._mode=u,this._rules=null;t=this._rules}if(0==t.length)return this._mode=u,this._rules=null;switch(t[0]&&t[0].__scope_link&&(t=[n(t[0].__scope_link,this.scopeRules)]),l(t[0])){case"object":this._mode=r,t=t[0];break;case"list":this._mode=r,t=t[0];break;default:this._mode=o}return this._rules=t,this._rules}return this._rules=null,this._mode=u,null}return this._rules=null,this._mode=u,null},walkTokenPath:function(t){if(0!=t.length){t=e.clone(t);var s;do s=t.shift();while(this._rules&&null!=this.walkByToken(s)&&t.length)}},getRules:function(){return this._rules},getNormalizedRules:function(){var e=l(this._rules);if(this._mode==r)switch(e){case"object":return["{"];case"list":return["["]}return this._rules}}),s.RuleWalker=i,s.getLinkedRules=n});