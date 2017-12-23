$(document).ready(function() {
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
                 "<p>~N after a phrase signifies slop amount</p>"
    });

    var submitFormFunction = function(e,buttonClass,buttonText,buttonTextWhileSearching,formId,successFunction) {
         e.preventDefault();

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

         //$('.loader').parent().show();
         $.ajax({
           type: 'POST',
           dataType: 'json',
           url: url,
           data: $form.serialize(),
           complete: function(jqxhr,status) {
             $button.prop('disabled',false).text(buttonText);
             $(window).scrollTop(tempScrollTop);
            // $('.loader').parent().hide();
           },
           error: function(jqxhr,status,error) {
             $('#results').html('<div style="color: red;">Server error during ajax request:'+error+'</div>');
           },
           success: successFunction
         });

         // remove orderings
         $form.find('.hidden-remove').remove();

         return false;
    };

    var successReportFromExcelOnly = function(data) {
        var $downloadForm = $('<form method="post" action="/secure/excel_generation"></form>');
        $downloadForm.appendTo('body').submit().remove();
    };

    var successReportFrom = function(data) {
       var $activePanes = $('#results-wrapper').find('li a.nav-link.active').filter(":first");
       var activePaneSelector = null;
       if($activePanes.length>0) {
           activePaneSelector = $activePanes.filter(':first').attr('href');
       }
       $('#results').html(data.message);
       if(activePaneSelector!==null) {
           $('#results').find('.tab-pane.active').removeClass('active show');
           $('#results-wrapper').find('li a.nav-link.active').removeClass('active');
           $('#results-wrapper').find('li a.nav-link[href="'+activePaneSelector+'"]').addClass('active');
           $(activePaneSelector).addClass('active').addClass('show');
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

       setCollapsibleHeaders('#results .collapsible-header');

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

                                        var $dailyBtn = $('<button data-group="day" class="btn btn-sm btn-secondary" type="button">Daily</button>');
                                        var $weeklyBtn = $('<button data-group="week" class="btn btn-sm btn-secondary" type="button">Weekly</button>');
                                        var $monthlyBtn = $('<button data-group="month" class="btn btn-sm btn-secondary" type="button">Monthly</button>');
                                        var $quarterlyBtn = $('<button data-group="quarter" class="btn btn-sm btn-secondary" type="button">Quarterly</button>');
                                        var $yearlyBtn = $('<button data-group="year" class="btn btn-sm btn-secondary" type="button">Yearly</button>');

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
           $('#results').html("<div style='color:red;'>JavaScript error occured while rendering charts: " + err.message + '</div>');
         }
       }
    };

    $('#generate-reports-form').submit(function(e) {
        $(this).find('#only-excel-hidden-input').val(false);
        var buttonClass = "generate-reports-form-button";
        var buttonText = "Generate Report";
        var buttonTextWhileSearching = "Generating...";
        var formId = $(this).attr('id');
        $('#results').html(''); // clears results div
        return submitFormFunction(e,buttonClass,buttonText,buttonTextWhileSearching,formId,successReportFrom);
    });
    $('.generate-reports-form-button').click(function(e) {
        e.preventDefault();
        $('#generate-reports-form').submit();
    });
    $('.download-to-excel-button').click(function(e) {
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
        var postSaveCallback = function() {
            window.location.href = '/secure/home'
        };
        var callback = function(data) {
            saveJSNodeFunction(null,null,name,true,data,'template',true,true,postSaveCallback);
        };
        return templateDataFunction(null,null,name,true,callback);
    });

    $('.update-default-attributes-button').click(function(e) {
        e.preventDefault();
        $('#update-default-attributes-form').submit();
    });

    // nested forms
    $('select.nested-filter-select').each(function() {
        $this = $(this);
        var displayItemSelectOptions = {width: '100%', placeholder: 'Search', closeOnSelect: true};
        $this.select2(displayItemSelectOptions);
    });


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
             if(!preventHighlight && $draggable.parent().is(':hidden')) {
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
                        $('#'+this).prop('disabled', false).filter('select').trigger('change', [preventHighlight]);
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

    $('.draggable .collapsible-header .remove-button').click(function(e) {
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
        closeOnSelect: false
    });

    $('.multiselect-ajax').each(function() {
        var $this = $(this);
        var url = $this.attr("data-url");
        $this.select2({
          width: "100%",
          ajax: {
            url: url,
            dataType: "json",
            delay: 100,
            data: function(params) {
                var query = {
                    search: params.term,
                    page: params.page || 1
                };

                return query;
            }
          }
        });
    });

    var getDatasetSelectData = function() {
        var $tree = $('#datasets-tree').jstree(true);
        return $($tree.get_json($tree, {
            flat: true
        }))
        .map(function(index, value) {
            var node = $tree.get_node(this.id);
            var lvl = node.parents.length;
            var idx = index;
            var preName="";
            $.each(node.parents,function() {
                preName+=this.toString()+"/";
            });
            if(node.type==='file'&&node.data.hasOwnProperty('user')&&node.data.hasOwnProperty('file')) {
                return {
                    id: node.data.user.concat(node.data.file.toString()),
                    text: preName.concat(node.text)
                };
            } else {
                return null;
            }
        }).filter(function() { return this!==null; });
    };

    $('.dataset-multiselect').select2({
        minimumResultsForSearch: 5,
        width: "100%",
    }).on('select2:opening',function(e) {
        createDatasetSelect2(this);
        return true;
    });

    var createDatasetSelect2 = function(elem) {
        // get datasets
        var $this = $(elem);
        var previousVal = $this.val();
        $this.select2('destroy');
        $this.select2({
            minimumResultsForSearch: 5,
            width: '100%',
            data: getDatasetSelectData()
        })
        $this.val(previousVal).trigger('change');
        $this.select2('open');
        $this.on('select2:opening', function(e) {
            createDatasetSelect2(elem);
            return true;
        });
        return $this;
    };

    $('.single-select2').select2({
        minimumResultsForSearch: 5,
        width: "100%"
    });

    setCollapsibleHeaders(".collapsible-header");

    $('#main-content-id').tooltip({
        content: function() { return $(this).attr('title'); },
        show: 500,
        hide: 100,
        delay: 500
    });

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
});


var resetSearchForm = function() {
    $('.attributeElement').not('.draggable').each(function() { $(this).find('select.nested-filter-select').filter(':first').val(null).trigger('change',[true]); });
    $('div.attribute').addClass("disabled");
    $('#results').html('');
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
    if(data===null) {
        alert("Error finding template.");
    } else if(data.hasOwnProperty('searchoptionsmap')) { // data came from li node
        showTemplateFormHelper("#searchOptionsForm",data["searchoptionsmap"]);
        showTemplateFormHelper("#attributesForm",data["attributesmap"]);
        showTemplateFormHelper("#filtersForm",data["filtersmap"]);
        showTemplateFormHelper("#chartsForm",data["chartsmap"]);
        try {
            showTemplateFormHelper("#highlightForm",data["highlightmap"]);
        } catch(err) {

        }
    } else if(data.hasOwnProperty('searchOptionsMap')) { // data came from newly added node
        showTemplateFormHelper("#searchOptionsForm",$.parseJSON(data["searchOptionsMap"]));
        showTemplateFormHelper("#attributesForm",$.parseJSON(data["attributesMap"]));
        showTemplateFormHelper("#filtersForm",$.parseJSON(data["filtersMap"]));
        showTemplateFormHelper("#chartsForm",$.parseJSON(data["chartsMap"]));
        try {
            showTemplateFormHelper("#highlightForm",data["highlightMap"]);
        } catch(err) {

        }
    } else if(data.hasOwnProperty('file')) {
        // need to get data
        var defaultFile = node === null;
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
        $.ajax({
            type: "POST",
            url: '/secure/get_template',
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
    $.ajax({
        type: "POST",
        url: '/secure/get_dataset',
        data: {
            file: data.file,
            shared: shared
        },
        success: function(data) {
            alert(data.assets);
        },
        dataType: "json"
    });
    return false;
};

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
     $.ajax({
         type: "POST",
         url: '/secure/rename_'+node_type,
         data: {
             name: newName,
             parentDirs: parents,
             file: file
         },
         success: function(data) {

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
     $.ajax({
         type: "POST",
         url: '/secure/delete_'+node_type,
         data: {
             path_to_remove: file,
             shared: shared
         },
         success: function(data) {
             tree.delete_node(node);
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

var templateDataFunction = function(tree,node,name,deletable,callback) {
    var preData = {};
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
    var nodeData = node;
    while(typeof nodeData.text !== 'undefined') {
        preData["parentDirs"].unshift(nodeData.text);
        var currId = nodeData.parent;
        nodeData = tree.get_node(currId);
    }
    callback(preData);
};

var assetListDatasetDataFunction = function(tree,node,name,deletable,callback) {
    // get user input
    var $input = $('#new-dataset-from-asset-list');
    var $container = $('#new-dataset-from-asset-list-overlay');
    var $submit = $('#new-dataset-from-asset-list-submit');
    var $cancel = $('#new-dataset-from-asset-list-cancel');

    $input.val('');
    $container.show();
    $submit.off('click');
    $cancel.off('click');
    $input.off('click');

    $input.click(function(e){
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

var saveJSNodeFunction = function(tree,node,name,deletable,preData,node_type,create,skipSuccessFunction,callback){
    if(preData!==null) {
        preData['defaultFile'] = skipSuccessFunction;
        $.ajax({
            type: "POST",
            url: '/secure/save_'+node_type,
            data: preData,
            success: function(data) {
                if(callback!==null) {
                    callback();
                }
                if(skipSuccessFunction) return;

                if(!data.hasOwnProperty('file')) {
                    alert('Error saving template: '+data.message);
                } else {
                    preData['file']=data['file'];
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
                                        saveJSNodeFunction(tree,node,name,deletable,data,node_type,true,false,null);
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
                                "title": "Update this "+node_type+" "+newItemSubLabel.toLowerCase()+".",
                                "action": function(obj) {
                                    var name = node.text;
                                    var callback = function(data) {
                                        saveJSNodeFunction(tree,node,name,deletable,data,node_type,false,false,null);
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
                if((node_type==='dataset') && !isFolder) {
                    items["Apply Current Form"] = {
                        "separator_before": false,
                        "separator_after": false,
                        "label": "Apply Current Form",
                        "title": "Generate report from the current form applied to this dataset.",
                        "action": function(obj) {
                            alert("Not yet implemented.");
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
        var tree = $(this).jstree(true);
        var node = tree.get_node(event.target);
        if(node.type==='file') {
            event.preventDefault();
            event.stopPropagation();
            dblclickFunction(node.data,tree,node);
            return false;
        }
        return true;
    });

};
