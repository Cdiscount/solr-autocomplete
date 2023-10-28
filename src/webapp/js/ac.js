$(function() {
 var URL_PREFIX = "http://localhost:8984/solr/autocomplete/autocomplete?suggest.dictionary=perso&q=";
 var URL_SUFFIX = "&wt=json";
 $("#searchBox").autocomplete({
 source : function(request, response) {
 var URL = URL_PREFIX + encodeURIComponent($("#searchBox").val()) + URL_SUFFIX;
 $.ajax({
 url : URL,
 success : function(data) {
 var docs = JSON.stringify(data.suggest.perso.response.hits);
 var jsonData = JSON.parse(docs); 
 console.debug(jsonData);
 response($.map(jsonData, function(value, key) { return { label : value.search } }));
 },
 dataType : 'jsonp',
 jsonp : 'json.wrf'
 });
 },
 minLength : 1
 })
 });