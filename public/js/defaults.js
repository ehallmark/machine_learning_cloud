$(document).ready(function() {
    $('.loader').show();

    setupJSTree("#templates-tree",showTemplateFunction,"template",[templateDataFunction],["From Current Form"]);
    setupJSTree("#datasets-tree",showDatasetFunction,"dataset",[lastGeneratedDatasetDataFunction,assetListDatasetDataFunction],["From Last Generated Report", "From Asset List", "From CSV File"]);
    $('.miniTip').miniTip({
        title: 'Advanced Keyword Syntax',
        event: 'click',
        content: "<p>+ signifies AND operation</p>"+
                 "<p>| signifies OR operation</p>"+
                 "<p>- negates a single token</p>"+
                 "\"\" wraps a number of tokens to signify a phrase for searching</p>"+
                 "<p>* at the end of a term signifies a prefix query</p>"+
                 "<p>( and ) signify precedence</p>"+
                 "<p>~N after a word signifies edit distance (fuzziness)</p>"+
                 "<p>~N after a phrase signifies slop amount (proximity searching)</p>"
    });

    $('.miniTip2').miniTip({
        title: 'Acclaim Expert Search Syntax',
        event: 'click',
        content: "<div>Supports most functionality available in the Acclaim Expert Search form.</div><div>Note: To query by research folder (RFID), please use the Dataset Name Filter.</div><div>Supported Fields: </div>"+$('#acclaim-supported-fields').html()
    });

    var submitFormFunction = function(e,buttonClass,buttonText,buttonTextWhileSearching,formId,successFunction) {
         e.preventDefault();

         var resultLimit = $('#main-options-limit').val();
         /*if(resultLimit) {
            if(resultLimit > 10000) {
                alert("Search for more than 10000 results may degrade performance.");
            }
         }*/

         var $form = $('#'+formId);
         var $button = $('.'+buttonClass);
         var url = $form.attr('action');
         var tempScrollTop = $(window).scrollTop();

         $button.prop('disabled',true).text(buttonTextWhileSearching);

         $(".attributeElement .attribute").not('.disabled').each(function() {
            var $this = $(this);
            var name = ".";
            while(name.includes(".")) {
                var $attributeElement = $this.parent().closest('.attributeElement');
                name = $attributeElement.attr('data-model');
                if(typeof name === 'undefined') return;
                var index = $attributeElement.parent().index();
                var $hiddenOrder = $('<input class="hidden-remove" type="hidden" name="order_'+ name +'" value="'+ index+'" />');
                $form.append($hiddenOrder);
                $this = $attributeElement;
            }
         });

         $.ajax({
           type: 'POST',
           dataType: 'json',
           url: url,
           data: $form.serialize(),
           complete: function(jqxhr,status) {
             $button.prop('disabled',false).text(buttonText);
             $(window).scrollTop(tempScrollTop);
           },
           error: function(jqxhr,status,error) {
             if(jqxhr.status==404 || jqxhr.status==502) {
                alert("Unable to establish connection to platform. Try refreshing page. Error code: "+jqxhr.status.toString());
             } else if(jqxhr.status==302) {
                alert("Must sign in again. Try refreshing page. Error code: "+jqxhr.status.toString());
             }
             $('#results .tab-pane .content').html('<div style="color: red;">Server error during ajax request:'+error+'</div>');
           },
           success: successFunction
         });

         // remove orderings
         $form.find('.hidden-remove').remove();

         return false;
    };

    var successReportFromExcelOnly = function(data) {
        var urlPrefix = $('#url-prefix').attr('prefix');
        if(!urlPrefix) {
            urlPrefix = "/secure";
        }
        var $downloadForm = $('<form method="post" action="/'+urlPrefix+'/excel_generation"></form>');
        $downloadForm.appendTo('body').submit().remove();
    };

    var successReportFrom = function(data) {
       var $tabs = $('#results').find('.tab-pane .content');
       try {
           var $content = $(data.message).children();
           $tabs.each(function(i,e){
               $(this).html($content.get(i));
           });
       } catch(ex) {
           $tabs.each(function(i,e){
               $(this).html(data.message);
           });
       }
       if($('#results #data-table table thead th').length > 0) {
           $('#results #data-table table').dynatable({
             dataset: {
               ajax: true,
               ajaxUrl: 'dataTable.json',
               ajaxOnLoad: true,
               records: []
             },
             features: {
                pushState: false
             }
           });
       } //else {
           //alert("Please include some attributes in the Attributes section.");
       //}

       setCollapsibleHeaders('#results .tab-pane .content .collapsible-header');

       if(data.hasOwnProperty('tableCnt')) {
            var tableCnt = data.tableCnt;
            for(var i = 0; i < tableCnt; i++) {
                var tableId = 'table-'.concat(String(i));
                var $table = $('#'+tableId);
                if($table.find('table thead th').length > 0) {
                   $table.find('table').dynatable({
                     dataset: {
                       ajax: true,
                       ajaxUrl: 'dataTable.json?tableId='.concat(String(i)),
                       ajaxOnLoad: true,
                       records: []
                     },
                     features: {
                        pushState: false
                     }
                   });
                }
            }
       }

       if (data.hasOwnProperty('chartCnt')) {
         try {
           var chartCnt = data.chartCnt;
           if(chartCnt > 0) {
             for(var i = 0; i<chartCnt; i++) {
               var idx = i;
               $.ajax({
                 type: "POST",
                 dataType: "json",
                 url: "charts",
                 data: { chartNum: idx },
                 success: function(chartData) {
                   if(chartData.hasOwnProperty('message')) {
                    // error
                      alert(chartData.message);
                   } else {
                      var $chartDiv = $('#'+chartData.chartId.toString());
                      for(var j = 0; j < chartData.charts.length; j++) {
                        var $currChart = $('<div id="'+ chartData.chartId+"-"+j.toString() +'"></div>');
                        var isStockChart = chartData.isStockCharts[j];
                        $currChart.appendTo($chartDiv);
                        var chartJson = chartData.charts[j];
                        chartJson['index']=j;
                        var chart;
                        if(isStockChart) {
                            // append data with data groupings
                            var buildStockChartCallback = function(chartData,j,chartJson) {
                                return Highcharts.stockChart(chartData.chartId+"-"+j.toString(), chartJson);
                            };

                            var updateDatagroupingByIndex = function(j,chartJson,originalSeriesData) {
                                var currentChart;
                                var currentBtnGroup = "year";
                                originalSeriesData = JSON.parse(JSON.stringify(originalSeriesData));
                                var updateDatagrouping = function(chartJson,units) {
                                    chartJson['plotOptions']['series']['dataGrouping'] = {
                                        enabled: true,
                                        force: true,
                                        approximation: 'sum',
                                        units: units
                                    };
                                    currentChart.destroy();
                                    chartJson['series'] = originalSeriesData;
                                    currentChart = buildStockChartCallback(chartData,j,chartJson);
                                    currentChart.redraw();
                                    return currentChart;
                                };
                                chartJson['rangeSelector'] = { enabled: false };
                                chartJson['plotOptions']['series']['dataGrouping'] = {
                                    enabled: true,
                                    force: true,
                                    approximation: 'sum',
                                    units: [['year',[1]]]
                                };
                                chartJson['chart']['events'] = {
                                    load: function() {
                                        var $btns = $('<div></div>');
                                        $btns.append('<label style="float: left; margin-bottom: 5px;">Group Dates By</label>');
                                        var $btnGroup = $('<div class="btn-group" style="margin-bottom: 5px;" role="group"></div>');
                                        $btns.append($btnGroup);

                                        var previous = chartJson['plotOptions']['series']['dataGrouping']['units'];

                                        var $dailyBtn = $('<button data-group="day" class="btn btn-sm btn-outline-secondary" type="button">Daily</button>');
                                        var $weeklyBtn = $('<button data-group="week" class="btn btn-sm btn-outline-secondary" type="button">Weekly</button>');
                                        var $monthlyBtn = $('<button data-group="month" class="btn btn-sm btn-outline-secondary" type="button">Monthly</button>');
                                        var $quarterlyBtn = $('<button data-group="quarter" class="btn btn-sm btn-outline-secondary" type="button">Quarterly</button>');
                                        var $yearlyBtn = $('<button data-group="year" class="btn btn-sm btn-outline-secondary" type="button">Yearly</button>');

                                        var updateFunction = function(btn,units) {
                                            currentBtnGroup = $(btn).attr("data-group");
                                            updateDatagrouping(chartJson,units);
                                        }

                                        $dailyBtn.click(function() {
                                            updateFunction(this,[['day',[1]]]);
                                        });
                                        $weeklyBtn.click(function() {
                                            updateFunction(this,[['week',[1]]]);
                                        });
                                        $monthlyBtn.click(function() {
                                            updateFunction(this,[['month',[1]]]);
                                        });
                                        $quarterlyBtn.click(function() {
                                            updateFunction(this,[['month',[3]]]);
                                        });
                                        $yearlyBtn.click(function() {
                                            updateFunction(this,[['year',[1]]]);
                                        });

                                        $btnGroup.append($dailyBtn);
                                        $btnGroup.append($weeklyBtn);
                                        $btnGroup.append($monthlyBtn);
                                        $btnGroup.append($quarterlyBtn);
                                        $btnGroup.append($yearlyBtn);

                                        $btnGroup.find('[data-group="'+currentBtnGroup+'"]').addClass('active');
                                        $(this.container).parent().prepend($btns)
                                    }
                                };

                                currentChart = buildStockChartCallback(chartData,j,chartJson);
                                return currentChart;
                            };
                            chart = updateDatagroupingByIndex(j,chartJson,chartJson.series);
                        } else {
                            chart = Highcharts.chart(chartData.chartId+"-"+j.toString(), chartJson);
                        }
                        chart.redraw();
                      }
                   }
                 }
               });
             }
           }
         } catch (err) {
           $('#results .tab-pane .content').html("<div style='color:red;'>JavaScript error occured while rendering charts: " + err.message + '</div>');
         }
       }
    };

    $('#generate-reports-form').submit(function(e) {
        e.preventDefault();
        $(this).find('#only-excel-hidden-input').val(false);
        var buttonClass = "generate-reports-form-button";
        var buttonText = "Generate Report";
        var buttonTextWhileSearching = "Generating...";
        var formId = $(this).attr('id');
        $('#results .tab-pane .content').html(''); // clears results div
        return submitFormFunction(e,buttonClass,buttonText,buttonTextWhileSearching,formId,successReportFrom);
    });
    $('.generate-reports-form-button').click(function(e) {
        e.preventDefault();
        $('#generate-reports-form').submit();
    });
    $('.download-to-excel-button').click(function(e) {
        e.preventDefault();
        $('#generate-reports-form').find('#only-excel-hidden-input').val(true);
        var buttonClass = "download-to-excel-button";
        var buttonText = "Download to Excel";
        var buttonTextWhileSearching = "Downloading...";
        var formId = 'generate-reports-form';
        return submitFormFunction(e,buttonClass,buttonText,buttonTextWhileSearching,formId,successReportFromExcelOnly);
    });

    $('#update-default-attributes-form').submit(function(e) {
        e.preventDefault();
        var name = 'default';
        var urlPrefix = $('#url-prefix').attr('prefix');
        var extract_to_usergroup = false;
        var $extractUG = $('#extract_to_usergroup');
        if($extractUG.length>0) {
            extract_to_usergroup = $extractUG.is(':checked');
        };
        if(!urlPrefix) {
            urlPrefix = "/secure";
        }
        var postSaveCallback = function() {
            window.location.href = urlPrefix+'/home'
        };
        var callback = function(data) {
            saveJSNodeFunction(null,null,name,true,data,'template',true,true,postSaveCallback,false);
        };
        return templateDataFunction(null,null,name,true,callback,extract_to_usergroup);
    });

    $('.update-default-attributes-button').click(function(e) {
        e.preventDefault();
        $('#update-default-attributes-form').submit();
    });

    var displayItemSelectOptions = {width: '100%', placeholder: 'Search', closeOnSelect: true};
    // nested forms

    $('select.nested-filter-select').each(function(){
        $(this).attr('title', 'Click to view available options...');
    });
    $('select.nested-filter-select').select2(displayItemSelectOptions);

    var nestedFilterSelectFunction = function(e,preventHighlight) {
         var $options = $(e.currentTarget.selectedOptions);
         var $select = $(this);
         var $selectWrapper = $select.parent().parent();
         var addedDraggables = [];

         //if(!child) { // disable full subtree tree
             var $hiddenOptions = $(e.currentTarget).find("option");
             if($options.length>0) { $hiddenOptions = $hiddenOptions.not($options); }
             $hiddenOptions.each(function(i,option){
                 var id = $(option).val();
                 var $draggable = $selectWrapper.find('.attributeElement[data-model="'+id+'"]');

                 // update attributes
                 var divAttribute = $draggable.attr('data-attribute');
                 if(divAttribute) {
                    $draggable.find('#'+divAttribute).addClass("disabled");
                 }
                 var inputs = $draggable.data('inputs');
                 if(inputs && inputs.length > 0) {
                    $.each(inputs,function() {
                        var $this = $('#'+this);
                        if($this.length == 0) {
                            alert('Missing: '+this);
                        } else {
                            $this.prop('disabled',true);
                            if($this.attr('type')==='checkbox') {
                                $this.prop('checked', false);
                            } else {
                                $this.val(null).filter('select').trigger('change', [preventHighlight]);
                            }
                        }
                    });
                 }

                 $draggable.parent().hide();
                 return true;
             });
         //}

         $options.each(function(i,option){
             var id = $(option).val();
             var $draggable = $selectWrapper.find('.attributeElement[data-model="'+id+'"]');
             var wasHidden = $draggable.parent().is(':hidden');
             if(!preventHighlight && wasHidden) {
                 addedDraggables.push($draggable);
             }

             // update attributes
             var divAttribute = $draggable.attr('data-attribute');
             if(divAttribute) {
                $draggable.find('#'+divAttribute).removeClass("disabled");
             }
             var inputs = $draggable.data('inputs');
             if(inputs && inputs.length > 0) {
                $.each(inputs,function() {
                    var $this = $('#'+this);
                    if($this.length == 0) {
                        alert('Missing: '+this);
                    } else {
                        $('#'+this).prop('disabled', false).trigger('focus').filter('select').trigger('change', [preventHighlight]);
                    }
                });
             }

             $draggable.parent().show();
         });

         // sort this
         $selectWrapper.find('.nested-form-list').filter(':first').each(function() {
             var list = $(this);
             var elems = list.children().filter('[sort-order]').detach();
             if(elems.length>0) {
                 elems.sort(function(a,b) {
                     var i = parseInt($(a).attr("sort-order"));
                     var j = parseInt($(b).attr("sort-order"));
                     return (i > j) ? 1 : (i < j) ? -1 : 0;
                 });
                 // remove sort
                 elems.removeAttr("sort-order");
                 list.append(elems);
                 list.sortable('refreshPositions');
             }
         });


         if(addedDraggables.length == 1) { // added by human
             $selectWrapper.find('.highlight').removeClass('highlight');
             $.each(addedDraggables, function() {
                 var $draggable = $(this);
                 $draggable.addClass('highlight');
                 $draggable.click(function() { // highlight until clicked
                     $(this).removeClass('highlight');
                 });
                 var $parent = $draggable.parent();
                 $parent.parent().prepend($parent);
             });
         }
         $('#acclaim_expert_filter').trigger('scroll');
         return true;
    }

    $('select.nested-filter-select').on("change", nestedFilterSelectFunction);

    // THIS FUNCTION HANDLES "POP-OVER" OF SELECTED FILTERS INTO ATTRIBUTES
    /*
    var $attrSelect = $('#multiselect-nested-filter-select-attributes');
    $('#filters-row select.nested-filter-select').on("select2:select", function(e) {
        var $elem = $(e.params.data.element);
        var $parent = $elem.parent();
        if($parent.is('optgroup')) {
            var attrName = $parent.attr('name');
            var parentName = null;
            var childName = null;
            if(attrName.includes('.')) {
                parentName = attrName.split('.')[0];
                childName = attrName.split('.')[1];
            } else {
                parentName = attrName;
            }

            // handle parent
            var values = $attrSelect.val();
            if(!values.includes(parentName)) {
                values.push(parentName);
                $attrSelect.val(values).trigger('change', [true]);
            }

            // handle any children
            if(childName != null) {
                var $childAttrSelect = $('#multiselect-nested-filter-select-'+parentName);
                if($parent.length > 0) {
                    // handle parent
                    var childValues = $childAttrSelect.val();
                    if(!childValues.includes(attrName)) {
                        childValues.push(attrName);
                        $childAttrSelect.val(childValues).trigger('change', [true]);
                    }
                }
            }
        }
    });*/

    $('.sidebar .nav-item .btn').click(function(e){
        $('.sidebar .nav-item .btn').removeClass('active');
        $(this).addClass('active');
    });

    $('.attributeElement .collapsible-header .remove-button').click(function(e) {
        e.stopPropagation();
        // get name
        var label = $(this).attr('data-model');
        var $select = $($(this).attr('data-select'));
        var values = $select.val();
        var idx = $.inArray(label,values);
        if(idx >= 0) {
            values.splice(idx,1);
            $select.val(values).trigger('change',[true]);
        }
    });


    $('.multiselect').select2({
        minimumResultsForSearch: 5,
        closeOnSelect: false,
        templateSelection: select2SelectedFunction
    });

    // how results look in the dropdown
    var ajaxMultiTemplateResultFunction = function(elem) {
        if (!elem.html_result) {
            return elem.text;
        }
        return $(elem.html_result);
    };

    $('.multiselect-ajax').select2({
      width: "100%",
      ajax: {
        url: function() { return $(this).attr("data-url"); },
        dataType: "json",
        delay: 100,
        data: function(params) {
            var query = {
                search: params.term,
                page: params.page || 1
            };

            return query;
        }
      },
      templateResult: ajaxMultiTemplateResultFunction
    });

    $('.single-select2').select2({
        minimumResultsForSearch: 10,
        width: "100%",
        templateSelection: select2SelectedFunction
    });

    // handle collect by for pivot tables
    var numericAttributes = $('#numeric-attributes-list');
    if(numericAttributes&&numericAttributes.attr('value')) {
        numericAttributes = JSON.parse(numericAttributes.attr('value'));
    }
    $('.collect-by-select').select2({
        minimumResultsForSearch: 10,
        width: "100%",
        templateSelection: select2SelectedFunction
    }).on('change', function(e) {
        var val = $(this).val();
        var $type = $(this).closest('.collect-container').find('select.collect-type');
        if(val) {
            var prevType = $type.val();
            // update type select from selected value
            var usePrevType = false;
            if(!numericAttributes.includes(val)) {
                if(prevType && ['Count','Cardinality'].includes(prevType)) {
                    usePrevType=true;
                }

                $type.empty();
                $type.append('<option value="Cardinality">Distinct Count</option>');
                $type.append('<option value="Count">Total Count</option>');
            } else {
                if(prevType && ['Sum','Average','Max','Min','StdDeviation','Variance'].includes(prevType)) {
                    usePrevType=true;
                }

                $type.empty();
                $type.append('<option value="Sum">Sum</option>');
                $type.append('<option value="Average">Average</option>');
                $type.append('<option value="Max">Max</option>');
                $type.append('<option value="Min">Min</option>');
                $type.append('<option value="StdDeviation">Standard Deviation</option>');
                $type.append('<option value="Variance">Variance</option>');
                $type.append('<option value="Count">Total Count</option>');
                $type.append('<option value="Cardinality">Distinct Count</option>');
            }
            $type.select2({
                minimumResultsForSearch: 10,
                width: "100%",
                templateSelection: select2SelectedFunction
            });
            if(usePrevType) {
                $type.val(prevType).trigger('select2.change');
            }
        } else {
            $type.val(null).trigger('select2.change');
        }
    });

    setCollapsibleHeaders(".collapsible-header");

    var $nestedLists = $('.nested-form-list');
    $nestedLists.sortable();
    $nestedLists.disableSelection();

    $('#main-content-id').addClass('show');

    $('input[type="checkbox"]').click(function(e) {
        if($(this).prop("checked")==true) {
            $(this).val('on');
        } else {
            $(this).val('off');
        }
        return true;
    });

    $( ".datepicker" ).datepicker({
       changeMonth: true,
       changeYear: true,
       dateFormat: 'yy-mm-dd'
    });
    $('#sidebar-jstree-wrapper').show();

    resetSearchForm();
    showTemplateFunction({file: 'default'},null,null);

    $(document).uitooltip({
        content: function() {
            return $(this).attr('title');
        },
        //html: true,
        show: {
            delay: 400,
            duration: 200
        }
    });

    function createRichInput(original) {
         var newId = "richtext_" + $(original).attr('id');
         var newElement = "<div class=\"richtext\" id=\"" + newId + "\"></div>";
         var $parent = $(original).parent();
         newId = '#' + newId;
         $(original).wrap(newElement);
         $(original).before("<pre></pre>");
         $(newId).before('<br>');

         $(newId + '> pre').css({
            'position': 'absolute',
            'margin': 0,
            'display': 'block',
            'padding': 0,
            'text-align': 'left'
         });

         $(original).css({
             'background': 'transparent',
             'position': 'relative',
             'z-index': 100,
             'margin': 0,
             'border': 0,
             'padding': 0
         });

         $(original).bind('propertychange keyup input paste click focus', function() {
             $(original).trigger('scroll');
             var text = $(original).val();
             $(newId + "> pre").html(colorize(text, $(original).getCursorPosition()));
         });
         $(original).scroll(function() {
         		//alert('scrolltop: '+$(this).scrollTop());
         	 var parentHeight = $parent.height();
         	 var parentWidth = $parent.width();
         	 //$(original).height(parentHeight).width(parentWidth);
             //$(newId).outerHeight(parentHeight).outerWidth(parentWidth);
             $(newId + '> pre').outerHeight(parentHeight).outerWidth(parentWidth);
             $(newId + '> pre').css({
           	     'top': $(this).position().top,
                 'left': $(this).position().left,
                 'height': $(this).height(),
                 'width': $(this).width()
             }).scrollTop($(this).scrollTop());
         });
         resizeable($(original), function() {
           	 $(original).trigger('scroll');
         });
    }

    function resizeable($textareas, func) {
         // set init (default) state
         $textareas.data('x', $textareas.outerWidth());
         $textareas.data('y', $textareas.outerHeight());

         $textareas.mouseup(function () {

             var $this = $(this);

             if ($this.outerWidth() != $this.data('x') || $this.outerHeight() != $this.data('y')) {
                 func();
             }

             // set new height/width
             $this.data('x', $this.outerWidth());
             $this.data('y', $this.outerHeight());
         });
    };

    function colorize(text, pos) {
         var i = 0, current_times = 0;
         var startc = '(', endc = ')';
         var current = -1;

         var entities = {'>': '&gt;','<':'&lt;'};
         var p2 = 0;
         var regex = new RegExp(Object.keys(entities).join("|"),'g');
         var converted = text.replace(regex, function(x, j) {
             if(pos > j) p2 += entities[x].length - 1;
             return entities[x];
         });

         pos += p2;
         var parens = [], indices = [], o = {};
         var newText = converted.replace(/\n$/g, '\n\n').replace(/((?:\\)*)([()])/g, function(full, escape, x, idx) {
             var len = escape.split(/\\/g).length - 1;
             if (len % 2 == 0) {
                 indices.push(idx);
                 if (x == startc) ++i;
                 o[idx] = { selected: false, type: x, depth: i, idx: idx, pair: -1, extra: escape };
                 if (idx == pos) o[idx].selected = true;
                 if (x == startc) parens.push(idx);
                 else {
                     if (parens.length > 0) {
                         var p = parens.pop();
                         o[idx].pair = p;
                         if (o[p].selected) o[idx].selected = true;
                         o[p].pair = idx;
                         if (o[idx].selected) o[p].selected = true;
                     }
                     --i
                 }
             }
         });
         newtext = converted;
         indices = indices.sort(function(x,y) { return Number(y) - Number(x); });
         indices.forEach(function(i) {
             newtext = newtext.substr(0,i) + o[i].extra +
             "<span class='" + (o[i].pair == -1 ? "unmatched " : "paren_" + (o[i].depth % 5)) +
             (o[i].selected ? " selected_paren": "") + "'>" + o[i].type + "</span>" +
             newtext.substr(i + 1 + o[i].extra.length)
         });
         return newtext+'\n';
    }

    (function($) {
         $.fn.getCursorPosition = function() {
             var input = this.get(0);
             if (!input) return; // No (input) element found
             if ('selectionStart' in input) {
                 // Standard-compliant browsers
                 return input.selectionStart;
             } else if (document.selection) {
                 // IE
                 input.focus();
                 var sel = document.selection.createRange();
                 var selLen = document.selection.createRange().text.length;
                 sel.moveStart('character', -input.value.length);
                 return sel.text.length - selLen;
             }
         }
    })(jQuery);

    var editor = CodeMirror.fromTextArea(document.getElementById('acclaim_expert_filter'), {
        lineNumbers: true
    });
    //createRichInput('#acclaim_expert_filter');
});


var resetSearchForm = function() {
    $('.attributeElement').not('.draggable').each(function() { $(this).find('select.nested-filter-select').filter(':first').val(null).trigger('change',[true]); });
    $('div.attribute').addClass("disabled");
    $('#results .tab-pane .content').html('');
};

var findByValue = function(inputs, value) {
    for(var i = 0; i < inputs.length; i++) {
        if(inputs[i].value == value) return inputs[i];
    }
    return null;
};


var setCollapsibleHeaders = function(selector) {
    $(selector).click(function () {
        $header = $(this);
        //getting the next element
        $content = $($header.attr("data-target"));
        if((! $content.hasClass("show")) && $content.attr("id")==="main-content-id") {
            $("html, body").animate({
                scrollTop: 0
            }, 500);
        }
        if($content.hasClass("show")) {
            $content.css("max-height", "none");
            var height = $content.outerHeight();
            $content.css("max-height",height.toString());
            $content.animate({
                maxHeight: 0
            }, {
                duration: 400,
                always: function() {
                    $content.removeClass("show");
                    $content.css("max-height","");
                }
            });

        } else {
            // get height
            $content.css("max-height", "none");
            var height = $content.outerHeight();

            // reset to 0 then animate with small delay
            $content.addClass("show");
            $content.css("max-height", "0");
            $content.animate({
                maxHeight: height
            }, {
                duration: 400,
                always: function() {
                    $content.css("max-height", "");
                }
            });
        }
    });
};

var showTemplateFormHelper = function(formSelector,dataMap) {
    $.each(dataMap,function(id,value) {
        if(!id.startsWith("order_")) {
            var order = null;
            if(dataMap.hasOwnProperty("order_"+id)) {
                order = dataMap["order_"+id];
            }

            var $elem = $('#'+id);
            var $draggable = $elem.closest(".attributeElement");
            $draggable.parent().show();
            if(order!==null&& ($elem.is('select.nested-filter-select') || $elem.is('div.attribute'))) {
                $draggable.parent().attr("sort-order",order);
            }
            if($elem.attr('type')==="checkbox") {
                $elem.prop('checked',value==='on');
            }
            if($elem.hasClass('multiselect-ajax')) {
                var labeledValues = [];
                var needToFindLabels = [];
                for(var i = 0; i < value.length; i++) {
                    var val = value[i];
                    if($elem.find('option[value="'+val+'"]').length) {
                        labeledValues.push(val); // good to go
                    } else {
                        needToFindLabels.push(val); // not good :(
                    }
                }
                $elem.val(labeledValues).trigger('change');
                if(needToFindLabels.length > 0) {
                  // get label
                  $.ajax({
                    type: "GET",
                    url: $elem.attr("data-url"),
                    data: {
                      get_label_for: needToFindLabels
                    },
                    success: function(data) {
                      if(data.hasOwnProperty('labels')&&data.hasOwnProperty('values')) {
                        for(var i = 0; i < data.labels.length; i++) {
                          var newVal = new Option(data.labels[i],data.values[i],true,true);
                          $elem.append(newVal);
                        }
                      }
                    },
                    error: function(jqXHR, exception) {
                      for(var i = 0; i < needToFindLabels.length; i++) {
                        var val = needToFindLabels[i];
                        var newVal = new Option(val,val,true,true);
                        $elem.append(newVal);
                      }
                    },
                    dataType: "json"
                  });
                }

            } else {
                $elem.val(value);
            }
            $elem.not('select.nested-filter-select').trigger('change');
        }
    });
    $(formSelector+' select.nested-filter-select').filter(':first').trigger('change', [true]);
};

var showTemplateFunction = function(data,tree,node){
    if(node!==null){ resetSearchForm(); }
    var $loaders = $('.loader');
    $loaders.show();
    if(data===null) {
        alert("Error finding template.");
        $loaders.hide();
    } else if(data.hasOwnProperty('searchoptionsmap')) { // data came from li node
        showTemplateFormHelper("#searchOptionsForm",data["searchoptionsmap"]);
        showTemplateFormHelper("#attributesForm",data["attributesmap"]);
        showTemplateFormHelper("#filtersForm",data["filtersmap"]);
        showTemplateFormHelper("#chartsForm",data["chartsmap"]);
        try {
            showTemplateFormHelper("#highlightForm",data["highlightmap"]);
        } catch(err) {

        }
        $loaders.hide();
    } else if(data.hasOwnProperty('searchOptionsMap')) { // data came from newly added node
        showTemplateFormHelper("#searchOptionsForm",$.parseJSON(data["searchOptionsMap"]));
        showTemplateFormHelper("#attributesForm",$.parseJSON(data["attributesMap"]));
        showTemplateFormHelper("#filtersForm",$.parseJSON(data["filtersMap"]));
        showTemplateFormHelper("#chartsForm",$.parseJSON(data["chartsMap"]));
        try {
            showTemplateFormHelper("#highlightForm",data["highlightMap"]);
        } catch(err) {

        }
        $loaders.hide();
    } else if(data.hasOwnProperty('file')) {
        // need to get data
        var defaultFile = node === null || !node.data.deletable;
        var shared = false;
        if(node!==null) {
            var nodeData = node;
            var parents = [];
            while(typeof nodeData.text !== 'undefined') {
                if(nodeData.type==='folder') {
                    parents.unshift(nodeData.text);
                }
                var currId = nodeData.parent;
                nodeData = tree.get_node(currId);
            }
            shared = parents.length > 0 && parents[0].startsWith("Shared");
        }
        var urlPrefix = $('#url-prefix').attr('prefix');
        if(!urlPrefix) {
            urlPrefix = "/secure";
        }
        $.ajax({
            type: "POST",
            url: urlPrefix+'/get_template',
            data: {
                file: data.file,
                shared: shared,
                defaultFile: defaultFile
            },
            success: function(data) {
                showTemplateFunction(data,null,null);
            },
            dataType: "json"
        });
    }
    return false;
};

var showDatasetFunction = function(data,tree,node){
    // need to get data
    var nodeData = node;
    var parents = [];
    while(typeof nodeData.text !== 'undefined') {
        if(nodeData.type==='folder') {
            parents.unshift(nodeData.text);
        }
        var currId = nodeData.parent;
        nodeData = tree.get_node(currId);
    }
    var shared = parents.length > 0 && parents[0].startsWith("Shared");
    $('#filters-row .attributeElement').not('.draggable').each(function() { $(this).find('select.nested-filter-select').filter(':first').val(null).trigger('change',[true]); });
    $('#filters-row div.attribute').addClass("disabled");
    var $datasetInput = $('#multiselect-multiselect-datasetNameInclude_filter');
    var $filter = $('#multiselect-nested-filter-select-attributesNested_filter');
    $filter.val([$datasetInput.attr('name')]).trigger('change');
    var name = data.file+"_"+data.user;
    if(!$datasetInput.find('option[value="'+name+'"]').length) {
        var newVal = new Option(node.text,name,true,true);
        $datasetInput.append(newVal);
    }
    $datasetInput.val(name).trigger('change');
    return false;
};

var showMultipleDatasetFunction = function(data,tree,node){
    // need to get data
    var nodeData = node;
    var parents = [];
    while(typeof nodeData.text !== 'undefined') {
        if(nodeData.type==='folder') {
            parents.unshift(nodeData.text);
        }
        var currId = nodeData.parent;
        nodeData = tree.get_node(currId);
    }
    var shared = parents.length > 0 && parents[0].startsWith("Shared");
    $('#filters-row .attributeElement').not('.draggable').each(function() { $(this).find('select.nested-filter-select').filter(':first').val(null).trigger('change',[true]); });
    $('#filters-row div.attribute').addClass("disabled");
    var $datasetInput = $('#multiselect-multiselect-datasetNameInclude_filter');
    var $filter = $('#multiselect-nested-filter-select-attributesNested_filter');
    $filter.val([$datasetInput.attr('name')]).trigger('change');
    var names = [];
    if(node.children) {
        if(node.children.length > 0) {
            for(var i = 0; i < node.children.length; i++) {
                var child = node.children[i];
                child = tree.get_node(child);
                    if(child.type==='file') {
                    var file = child.data.file;
                    var user = child.data.user;
                    var name = file + "_" + user;
                    names.push(name);
                    if(!$datasetInput.find('option[value="'+name+'"]').length) {
                        var newVal = new Option(child.text,name,true,true);
                        $datasetInput.append(newVal);
                    }
                }
            }
        }
    }
    $datasetInput.val(names).trigger('change');
    return false;
};

var addMultipleDatasetFunction = function(data,tree,node){
    var $datasetInput = $('#multiselect-multiselect-datasetNameInclude_filter');

    // need to get data
    var names = $datasetInput.val();
    var nodeData = node;
    var parents = [];
    while(typeof nodeData.text !== 'undefined') {
        if(nodeData.type==='folder') {
            parents.unshift(nodeData.text);
        }
        var currId = nodeData.parent;
        nodeData = tree.get_node(currId);
    }
    var shared = parents.length > 0 && parents[0].startsWith("Shared");

    var $filter = $('#multiselect-nested-filter-select-attributesNested_filter');
    var prevFilters = $filter.val();
    if(prevFilters===null) {
        prevFilters = [];
    }
    if(!prevFilters.includes($datasetInput.attr('name'))) {
        prevFilters.push($datasetInput.attr('name'));
    }
    $filter.val(prevFilters).trigger('change');
    if(node.children) {
        if(node.children.length > 0) {
            for(var i = 0; i < node.children.length; i++) {
                var child = node.children[i];
                child = tree.get_node(child);
                    if(child.type==='file') {
                    var file = child.data.file;
                    var user = child.data.user;
                    var name = file + "_" + user;
                    if(!names.includes(name)) {
                        names.push(name);
                        if(!$datasetInput.find('option[value="'+name+'"]').length) {
                            var newVal = new Option(child.text,name,true,true);
                            $datasetInput.append(newVal);
                        }
                    }
                }
            }
        }
    }
    $datasetInput.val(names).trigger('change');
    return false;
};

var select2SelectedFunction = function(item) {
  var $option = $(item.element);
  var $optGroup = $option.parent();
  if($optGroup.is("optgroup") && $optGroup.get(0).label!==item.text) {
      return item.text + " of "+$optGroup.get(0).label;
  } else {
      return item.text;
  }
}

var renameJSNodeFunction = function(tree,node,newName,file,node_type){
     var nodeData = tree.get_node(node.parent);
     var parents = [];
     while(typeof nodeData.text !== 'undefined') {
         if(nodeData.type==='folder') {
            parents.unshift(nodeData.text);
         }
         var currId = nodeData.parent;
         nodeData = tree.get_node(currId);
     }
     var urlPrefix = $('#url-prefix').attr('prefix');
     if(!urlPrefix) {
         urlPrefix = "/secure";
     }
     $.ajax({
         type: "POST",
         url: urlPrefix+'/rename_'+node_type,
         data: {
             name: newName,
             parentDirs: parents,
             file: file
         },
         success: function(data) {

         },
         error: function(jqxhr,status,error) {
             if(jqxhr.status==404) {
                alert("Unable to establish connection to platform. Try refreshing page.");
             }
         },
         dataType: "json"
     });
     return false;
};

var removeJSNodeFunction = function(tree,node,file,node_type){
     var nodeData = node;
     var parents = [];
     while(typeof nodeData.text !== 'undefined') {
         if(nodeData.type==='folder') {
             parents.unshift(nodeData.text);
         }
         var currId = nodeData.parent;
         nodeData = tree.get_node(currId);
     }
     var shared = parents.length > 0 && parents[0].startsWith("Shared");
     var urlPrefix = $('#url-prefix').attr('prefix');
     if(!urlPrefix) {
         urlPrefix = "/secure";
     }
     $.ajax({
         type: "POST",
         url: urlPrefix+'/delete_'+node_type,
         data: {
             path_to_remove: file,
             shared: shared
         },
         success: function(data) {
             tree.delete_node(node);
         },
         error: function(jqxhr,status,error) {
             if(jqxhr.status==404) {
                alert("Unable to establish connection to platform. Try refreshing page.");
             }
         },
         dataType: "json"
     });
     return false;
};

var saveTemplateFormHelper = function(containerSelector,itemSelector,dataMap,dataKey) {
        var tmpData = {};
        $(containerSelector+" "+itemSelector).find('textarea,input,select,div.attribute').each(function(i,e) {
            var $elem = $(this);
            var id = $elem.attr('id');
            if(id && ! ($elem.prop('disabled') || $elem.hasClass('disabled'))) {
                if(! (id in tmpData)) {
                    if($elem.attr('type')==='checkbox') {
                        if($elem.prop("checked")==true) {
                            $elem.val('on');
                        } else {
                            $elem.val('off');
                        }
                    }
                    tmpData[id]=$elem.val();
                    if($elem.is('select.nested-filter-select') || $elem.is('div.attribute')) {
                        tmpData["order_"+id]=i;
                    }
                }
            }
        });
        var json = JSON.stringify(tmpData);
        dataMap[dataKey] = json;
    };

var templateDataFunction = function(tree,node,name,deletable,callback,extract_to_usergroup) {
    var preData = {};
    if(extract_to_usergroup) {
        preData["extract_to_usergroup"] = true;
    }
    preData["name"]=name;
    saveTemplateFormHelper("#searchOptionsForm",".attributeElement",preData,"searchOptionsMap");
    saveTemplateFormHelper("#attributesForm",".attributeElement",preData,"attributesMap");
    saveTemplateFormHelper("#filtersForm",".attributeElement",preData,"filtersMap");
    saveTemplateFormHelper("#chartsForm",".attributeElement",preData,"chartsMap");
    saveTemplateFormHelper("#highlightForm",".attributeElement",preData,"highlightMap");
    preData["deletable"] = deletable;

    if(node!==null) {
        if(node.hasOwnProperty('data') && node.data.hasOwnProperty('file')) {
            preData["file"] = node.data.file;
        }
        if(node.hasOwnProperty('data') && node.data.hasOwnProperty('user')) {
            preData["user"] = node.data.user;
        }
        preData["parentDirs"] = [];
        var nodeData = node;
        while(typeof nodeData.text !== 'undefined') {
            if(nodeData.type==='folder') {
                preData["parentDirs"].unshift(nodeData.text);
            }
            var currId = nodeData.parent;
            nodeData = tree.get_node(currId);
        }
    }
    callback(preData);
};

var lastGeneratedDatasetDataFunction = function(tree,node,name,deletable,callback) {
    var preData = {};
    preData["name"]=name;
    preData["createDataset"]= true;
    preData["parentDirs"] = [];
    preData["deletable"] = deletable;
    if(node.hasOwnProperty('data') && node.data.hasOwnProperty('file')) {
        preData["file"] = node.data.file;
    }
    if(node.hasOwnProperty('data') && node.data.hasOwnProperty('user')) {
        preData["user"] = node.data.user;
    }
    var nodeData = node;
    while(typeof nodeData.text !== 'undefined') {
        if(nodeData.type==='folder') {
            preData["parentDirs"].unshift(nodeData.text);
        }
        var currId = nodeData.parent;
        nodeData = tree.get_node(currId);
    }
    callback(preData);
};

var getKFromClusterInputFunction = function(callbackWithValue) {
    // get user input
    var $input = $('#k-for-clustering');
    var $container = $('#k-for-clustering-overlay');
    var $inner = $('#k-for-clustering-inside');
    var $submit = $('#k-for-clustering-submit');
    var $cancel = $('#k-for-clustering-cancel');

    $input.val('');
    $container.show();
    $submit.off('click');
    $cancel.off('click');
    $input.off('click');
    $inner.off('click');

    $inner.click(function(e){
        e.stopPropagation();
    });
    $submit.click(function() {
        $container.hide();
        callbackWithValue($input.val());
    });
    $cancel.click(function() {
        $container.hide();
    });
    $container.click(function() {
        $container.hide();
    });

}

var assetListDatasetDataFunction = function(tree,node,name,deletable,callback) {
    // get user input
    var $input = $('#new-dataset-from-asset-list');
    var $container = $('#new-dataset-from-asset-list-overlay');
    var $submit = $('#new-dataset-from-asset-list-submit');
    var $cancel = $('#new-dataset-from-asset-list-cancel');
    var $inner = $('#new-dataset-from-asset-list-inside');

    $input.val('');
    $container.show();
    $submit.off('click');
    $cancel.off('click');
    $input.off('click');
    $inner.off('click');

    $inner.click(function(e){
        e.stopPropagation();
    });
    $submit.click(function() {
        var preData = {};
        preData["name"]=name;
        preData["assets"] = $input.val().split(/\s+/);
        preData["parentDirs"] = [];
        preData["deletable"] = deletable;
        if(node.hasOwnProperty('data') && node.data.hasOwnProperty('file')) {
            preData["file"] = node.data.file;
        }
        if(node.hasOwnProperty('data') && node.data.hasOwnProperty('user')) {
            preData["user"] = node.data.user;
        }
        var nodeData = node;
        while(typeof nodeData.text !== 'undefined') {
            if(nodeData.type==='folder') {
                preData["parentDirs"].unshift(nodeData.text);
            }
            var currId = nodeData.parent;
            nodeData = tree.get_node(currId);
        }
        $container.hide();
        callback(preData);
    });

    $cancel.click(function() {
        $container.hide();
    });
    $container.click(function() {
        $container.hide();
    });
};

var saveJSNodeFunction = function(tree,node,name,deletable,preData,node_type,create,skipSuccessFunction,callback,onlyUpdate){
    if(preData!==null) {
        preData['defaultFile'] = skipSuccessFunction;
        preData['addToAssets'] = onlyUpdate;
        var urlPrefix = $('#url-prefix').attr('prefix');
        if(!urlPrefix) {
            urlPrefix = "/secure";
        }
        $.ajax({
            type: "POST",
            url: urlPrefix+'/save_'+node_type,
            data: preData,
            error: function(jqxhr,status,error) {
                if(jqxhr.status==404) {
                   alert("Unable to establish connection to platform. Try refreshing page.");
                }
            },
            success: function(data) {
                if(callback!==null) {
                    callback();
                }
                if(skipSuccessFunction) return;

                if(!data.hasOwnProperty('file')&&!data.hasOwnProperty('user')) {
                    alert('Error saving template: '+data.message);
                } else {
                    preData['file']=data['file'];
                    preData['user']=data['user'];
                    var newData = {
                        'text': name,
                        'type': 'file',
                        'icon': 'jstree-file',
                        'jstree': {'type': 'file'},
                    };
                    $.each(preData, function(k,v) { newData[k] = v; });
                    if(create) {
                        node = tree.create_node(
                            node,
                            { 'data' : newData},
                            'first',
                            function(newNode) {
                                setTimeout(function() {
                                    newNode.data = newData;
                                    tree.edit(newNode,name,function(n,status,cancelled) {
                                        if(status && ! cancelled) {
                                            renameJSNodeFunction(tree,n,n.text,data['file'],node_type);
                                        }
                                    });
                                },0);
                            }
                        );
                    } else {
                        node.data = newData;
                    }
                }
            },
            dataType: "json"
        });
    }
    return false;
};

var removeDescendantsHelper = function(tree,node,node_type) {
    var isFolder = node.type==='folder';
    if(isFolder) {
        // get all children
        var children = node.children;
        for(var i = 0; i < children.length; i++) {
            var child = tree.get_node(children[i]);
            removeDescendantsHelper(tree,child,node_type);
        }
        tree.delete_node(node);
    } else {
        removeJSNodeFunction(tree,node,node.data.file,node_type)
    }
};

var renameDescendantsOfFolderHelper = function(tree,node,node_type) {
    var isFolder = node.type==='folder';
    if(isFolder) {
        // get all children
        var children = node.children;
        for(var i = 0; i < children.length; i++) {
            var child = tree.get_node(node.children[i]);
            renameDescendantsOfFolderHelper(tree,child,node_type);
        }

    } else {
        renameJSNodeFunction(tree,node,node.text,node.data.file,node_type);
    }
};

var loadEvent = function(){
 // do nothing :(
};

function capitalize(string)
{
    return string.charAt(0).toUpperCase() + string.slice(1);
}


function cleanArray(actual) {
  var newArray = new Array();
  for (var i = 0; i < actual.length; i++) {
    if (actual[i]) {
      newArray.push(actual[i]);
    }
  }
  return newArray;
}

var setupJSTree = function(tree_id, dblclickFunction, node_type, jsNodeDataFunctions, newItemSubLabels) {
    $(tree_id).jstree({
        "core" : {
            "multiple" : false,
            "check_callback": true
        },
        "contextmenu": {
            "items": function(node) {
                var items = {};
                var tree = $(tree_id).jstree(true);

                var isFolder = node.type==='folder';
                var topLevelFolder = isFolder && (node.parents.length === 1);
                var deletable = node.data.deletable;

                if(isFolder && deletable) {
                    items["New Folder"] = {
                        "separator_before": false,
                        "separator_after": false,
                        "label": "New Folder",
                        "title": "Create a new subdirectory.",
                        "action": function(obj) {
                            node = tree.create_node(node, {
                                'text': 'New Folder',
                                'type': 'folder',
                                'icon': 'jstree-folder',
                                'jstree': {'type': 'folder'},
                                'data' : {
                                    'deletable': deletable
                                }
                            });
                            tree.edit(node);
                        }
                    };
                    // must create a folder first in the shared environment
                    if(!(topLevelFolder && node.text.startsWith("Shared"))) {
                        var subMenu = {};
                        var labelToFunctions = {};
                        for(var i = 0; i < jsNodeDataFunctions.length; i++) {
                            var jsNodeDataFunction = jsNodeDataFunctions[i];
                            var newItemSubLabel = newItemSubLabels[i];
                            labelToFunctions[newItemSubLabel]=jsNodeDataFunction;
                            subMenu[newItemSubLabel] = {
                                "separator_before": false,
                                "separator_after": false,
                                "label": newItemSubLabel,
                                "title": "Create new "+node_type+" "+newItemSubLabel.toLowerCase()+".",
                                "action": function(obj) {
                                    var name = 'New '+capitalize(node_type);
                                    var callback = function(data) {
                                        saveJSNodeFunction(tree,node,name,deletable,data,node_type,true,false,null,false);
                                    };
                                    labelToFunctions[obj.item.label](tree,node,name,deletable,callback);
                                    return true;
                                }
                            }
                        }
                        var menuName = "New "+capitalize(node_type);
                        items[menuName] = {
                            "separator_before": false,
                            "separator_after": false,
                            "label": menuName,
                            "title": "Create a new "+node_type+".",
                            "submenu": subMenu
                        };
                    }
                }
                var subMenu = {};
                if(!topLevelFolder && deletable) {
                    items["Delete"] = {
                        "separator_before": false,
                        "separator_after": false,
                        "label": "Delete",
                        "title": "Permanently delete this "+(isFolder ? "folder" : node_type)+".",
                        "action": function(obj) {
                            removeDescendantsHelper(tree,node,node_type);
                            return true;
                        }
                    };
                    items["Rename"] = {
                        "separator_before": false,
                        "separator_after": false,
                        "label": "Rename",
                        "title": "Rename this "+(isFolder ? "folder" : node_type)+".",
                        "action": function(obj) {
                            if(isFolder) {
                                tree.edit(node,node.text,function(node,status,cancelled) {
                                    renameDescendantsOfFolderHelper(tree,node,node_type);
                                });
                            } else {
                                tree.edit(node,node.text,function(node,status,cancelled) {
                                    if(status && ! cancelled) {
                                        renameJSNodeFunction(tree,node,node.text,node.data.file,node_type);
                                    }
                                })
                            }
                            return true;
                        }
                    };
                    if(!isFolder) {
                        var menuName = "Apply "+capitalize(node_type);
                        items[menuName] = {
                            "separator_before": false,
                            "sepeartor_after": false,
                            "label": menuName,
                            "title": "Apply this "+node_type+".",
                            "action": function(obj) {
                                if(node.type==='file') {
                                    dblclickFunction(node.data,tree,node);
                                    return true;
                                }
                            }
                        };
                        var labelToFunctions = {};
                        for(var i = 0; i < jsNodeDataFunctions.length; i++) {
                            var jsNodeDataFunction = jsNodeDataFunctions[i];
                            var newItemSubLabel = newItemSubLabels[i];
                            labelToFunctions[newItemSubLabel]=jsNodeDataFunction;
                            subMenu[newItemSubLabel] = {
                                "separator_before": false,
                                "separator_after": false,
                                "label": newItemSubLabel,
                                "title": "Update this "+node_type+" "+newItemSubLabel.toLowerCase()+".",
                                "action": function(obj) {
                                    var name = node.text;
                                    var callback = function(data) {
                                        saveJSNodeFunction(tree,node,name,deletable,data,node_type,false,false,null,false);
                                    };
                                    labelToFunctions[obj.item.label](tree,node,name,deletable,callback);
                                    return true;
                                }
                            }
                        }
                        var menuName = "Update "+capitalize(node_type);
                        items[menuName] = {
                            "separator_before": false,
                            "separator_after": false,
                            "label": menuName,
                            "title": "Update this "+node_type+".",
                            "submenu": subMenu
                        };
                    }
                }
                if((node_type==='dataset') && isFolder) {
                    // plot children
                    var menuName = "Apply Children";
                    items[menuName] = {
                        "separator_before": true,
                        "separator_after": false,
                        "label": menuName,
                        "title": "Apply child datasets to current template.",
                        "action": function(obj) {
                            showMultipleDatasetFunction(node.data,tree,node);
                            return true;
                        }

                    };
                    // plot children
                    var menuName = "Add Children";
                    items[menuName] = {
                        "separator_before": false,
                        "separator_after": false,
                        "label": menuName,
                        "title": "Add child datasets to current template.",
                        "action": function(obj) {
                            addMultipleDatasetFunction(node.data,tree,node);
                            return true;
                        }

                    };

                }
                if((node_type==='dataset') && !isFolder && deletable) {
                    var addAssetsSubmenu = {};
                    var labelToFunctions = {};
                    for(var i = 0; i < jsNodeDataFunctions.length; i++) {
                        var jsNodeDataFunction = jsNodeDataFunctions[i];
                        var newItemSubLabel = newItemSubLabels[i];
                        labelToFunctions[newItemSubLabel]=jsNodeDataFunction;
                        addAssetsSubmenu[newItemSubLabel] = {
                            "separator_before": false,
                            "separator_after": false,
                            "label": newItemSubLabel,
                            "title": "Add assets to this dataset "+newItemSubLabel.toLowerCase()+".",
                            "action": function(obj) {
                                var name = node.text;
                                var callback = function(data) {
                                    saveJSNodeFunction(tree,node,name,deletable,data,node_type,false,false,null,true);
                                };
                                labelToFunctions[obj.item.label](tree,node,name,deletable,callback);
                                return true;
                            }
                        }
                    }
                    items["Add Assets"] = {
                        "separator_before": false,
                        "separator_after": false,
                        "label": "Add Assets",
                        "title": "Add assets to this dataset.",
                        "submenu": addAssetsSubmenu
                    };
                    items["Cluster Dataset"] = {
                        "separator_before": false,
                        "separator_after": false,
                        "label": "Cluster Dataset",
                        "title": "Cluster this dataset into technology categories.",
                        "action": function(obj) {
                            // num clusters
                            var callback = function(k) {
                                // need to get data
                                var nodeData = node;
                                var parents = [];
                                while(typeof nodeData.text !== 'undefined') {
                                    if(nodeData.type==='folder') {
                                        parents.unshift(nodeData.text);
                                    }
                                    var currId = nodeData.parent;
                                    nodeData = tree.get_node(currId);
                                }
                                var shared = parents.length > 0 && parents[0].startsWith("Shared");
                                var urlPrefix = $('#url-prefix').attr('prefix');
                                if(!urlPrefix) {
                                    urlPrefix = "/secure";
                                }
                                $.ajax({
                                    type: "POST",
                                    url: urlPrefix+'/cluster_dataset',
                                    data: {
                                        file: node.data.file,
                                        shared: shared,
                                        k: k
                                    },
                                    success: function(clusters) {
                                        if(!clusters.hasOwnProperty('clusters')) {
                                             alert('Error saving template: '+clusters.message);
                                        } else {
                                            var folderData = {
                                                'text': node.text,
                                                'deletable': true,
                                                'type': 'folder',
                                                'icon': 'jstree-folder',
                                                'jstree': {'type':'folder'}
                                            };
                                            tree.create_node(
                                                tree.get_node(node.parent),
                                                {'data' : folderData, 'text': node.text},
                                                'first',
                                                function(newFolder) {
                                                    newFolder.data=folderData;
                                                    $.each(clusters.clusters, function(idx,data){
                                                        if(data.hasOwnProperty('file')&&data.hasOwnProperty('user')&&data.hasOwnProperty('name')) {
                                                            var newData = {
                                                                'text': data.name,
                                                                'deletable': true,
                                                                'type': 'file',
                                                                'icon': 'jstree-file',
                                                                'jstree': {'type': 'file'},
                                                            };
                                                            $.each(data, function(k,v) { newData[k] = v; });
                                                            var newNode = tree.create_node(
                                                                newFolder,
                                                                { 'data' : newData, 'text': data.name},
                                                                'first',
                                                                function(newNode) {
                                                                    newNode.data=newData;
                                                                }
                                                            );
                                                        }
                                                    });
                                                }
                                            );

                                        }
                                    },
                                    dataType: "json"
                                });
                            };

                            getKFromClusterInputFunction(callback);
                            return true;
                        }
                    };
                }
                return items;
            }
        },
        "types": {
            "folder": {
                "icon": "jstree-folder"
            },
            "file" : {
                "icon": "jstree-file"
            }
        },
        "plugins": ["types","wholerow","sort","contextmenu"]
    });

    $(tree_id).bind("dblclick.jstree", function(event) {
        if( $('input:focus').length == 0 ) {
            var tree = $(this).jstree(true);
            var node = tree.get_node(event.target);
            if(node.type==='file') {
                event.preventDefault();
                event.stopPropagation();
                dblclickFunction(node.data,tree,node);
                return false;
            }
        }
        return true;
    });
    //$(tree_id).bind("hover_node.jstree", function(event,data) {
    //    $('#'+data.node.id).prop('title', 'Right click for more options...');
    //    return true;
    //});
};
