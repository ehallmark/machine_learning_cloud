$(document).ready(function() {
    var renameTemplateFunction = function(tree,node,newName,file){
         var nodeData = tree.get_node(node.parent);
         var parents = [];
         while(typeof nodeData.text !== 'undefined') {
             parents.unshift(nodeData.text);
             var currId = nodeData.parent;
             nodeData = tree.get_node(currId);
         }
         $.ajax({
             type: "POST",
             url: '/secure/rename_template',
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

    var removeTemplateFunction = function(tree,node,file){
         var nodeData = node;
         var parents = [];
         while(typeof nodeData.text !== 'undefined') {
             parents.unshift(nodeData.text);
             var currId = nodeData.parent;
             nodeData = tree.get_node(currId);
         }
         var shared = parents.length > 0 && parents[0].startsWith("Shared");
         $.ajax({
             type: "POST",
             url: '/secure/delete_template',
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
            if($elem.attr('id') && ! ($elem.prop('disabled') || $elem.hasClass('disabled'))) {
                tmpData[$elem.attr("id")]=$elem.val();
                tmpData["order_"+$elem.attr("id")]=i;
            }
        });
        var json = JSON.stringify(tmpData);
        dataMap[dataKey] = json;
    };


    var saveTemplateFunction = function(tree,node,name,deletable){
        var preData = {};
        preData["name"]=name;
        saveTemplateFormHelper("#searchOptionsForm",".attributeElement",preData,"searchOptionsMap");
        saveTemplateFormHelper("#attributesForm",".attributeElement",preData,"attributesMap");
        saveTemplateFormHelper("#filtersForm",".attributeElement",preData,"filtersMap");
        saveTemplateFormHelper("#chartsForm",".attributeElement",preData,"chartsMap");
        saveTemplateFormHelper("#highlightForm",".attributeElement",preData,"highlightMap");

        preData["parentDirs"] = []
        preData["deletable"] = deletable;
        var nodeData = node;
        while(typeof nodeData.text !== 'undefined') {
            preData["parentDirs"].unshift(nodeData.text);
            var currId = nodeData.parent;
            nodeData = tree.get_node(currId);
        }
        $.ajax({
          type: "POST",
          url: '/secure/save_template',
          data: preData,
          success: function(data) {
            if(!data.hasOwnProperty('file')) {
                alert('Error saving template: '+data.message);
            } else {
                preData['file']=data['file'];
                node = tree.create_node(node, {
                    'text': name,
                    'type': 'file',
                    'icon': 'jstree-file',
                    'jstree': {'type': 'file'},
                    'data' : {
                        preData
                    }
                });
                tree.edit(node,name,function(n,status,cancelled) {
                    //$('#'+node).dblclick(function(e) { showTemplateFunction(preData); });
                    if(status && ! cancelled) {
                        renameTemplateFunction(tree,n,n.text,data['file']);
                    }
                })
            }
          },
          dataType: "json"
        });

        return false;
    };

    var removeDescendantsHelper = function(tree,node) {
        var isFolder = node.type==='folder';
        if(isFolder) {
            // get all children
            var children = node.children;
            for(var i = 0; i < children.length; i++) {
                var child = tree.get_node(node.children[i]);
                removeDescendantsHelper(tree,child,child.data.file);
            }
            tree.delete_node(node);
        } else {
            removeTemplateFunction(tree,node,node.data.file)
        }
    };

    var renameDescendantsOfFolderHelper = function(tree,node) {
        var isFolder = node.type==='folder';
        if(isFolder) {
            // get all children
            var children = node.children;
            for(var i = 0; i < children.length; i++) {
                var child = tree.get_node(node.children[i]);
                renameDescendantsOfFolderHelper(tree,child,child.data.file);
            }

        } else {
            renameTemplateFunction(tree,node,node.text,node.data.file);
        }
    };


    var templates_tree_id = "#templates-tree";
    var jstree = $(templates_tree_id).jstree({
        "core" : {
            "multiple" : false,
            "check_callback": true
        },
        "contextmenu": {
            "items": function(node) {
                var items = {};
                var tree = $(templates_tree_id).jstree(true);

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
                    items["New Template"] = {
                        "separator_before": false,
                        "separator_after": false,
                        "label": "New Template",
                        "title": "Create a new template.",
                        "submenu": {
                            "From Current Form": {
                                "separator_before": false,
                                "separator_after": false,
                                "label": "From Current Form",
                                "title": "Create new template from current form.",
                                "action": function(obj) {
                                    var name = 'New Template';
                                    saveTemplateFunction(tree,node,name,deletable);
                                    return true;
                                }
                            }
                        }
                    };
                }
                if(!topLevelFolder && deletable) {
                    items["Delete"] = {
                        "separator_before": false,
                        "separator_after": false,
                        "label": "Delete",
                        "title": "Permanently delete this "+(isFolder ? "folder" : "template")+".",
                        "action": function(obj) {
                            removeDescendantsHelper(tree,node);
                            return true;
                        }
                    };
                    items["Rename"] = {
                        "separator_before": false,
                        "separator_after": false,
                        "label": "Rename",
                        "title": "Rename this "+(isFolder ? "folder" : "template")+".",
                        "action": function(obj) {
                            if(isFolder) {
                                tree.edit(node,node.text,function(node,status,cancelled) {
                                    renameDescendantsOfFolderHelper(tree,node);
                                });
                            } else {
                                tree.edit(node,node.text,function(node,status,cancelled) {
                                    if(status && ! cancelled) {
                                        renameTemplateFunction(tree,node,node.text,node.data.file);
                                    }
                                })
                            }
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

    $(templates_tree_id).bind("dblclick.jstree", function(event) {
        var tree = $(this).jstree(true);
        var node = tree.get_node(event.target);
        showTemplateFunction(node.data);
    });

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


    var submitFormFunction = function(e,onlyExcel) {
         e.preventDefault();
         var buttonId, buttonTextWhileSearching,buttonText;
         if(onlyExcel) {
            buttonId = "download-to-excel-button";
            buttonText = "Download to Excel";
            buttonTextWhileSearching = "Downloading...";
         } else {
            buttonId = "generate-reports-form-button";
            buttonText = "Generate Report";
            buttonTextWhileSearching = "Generating...";
         }
         var formId = "generate-reports-form";
         var $form = $('#'+formId);
         var $button = $('#'+buttonId);
         var url = $form.attr('action');
         var tempScrollTop = $(window).scrollTop();

         $button.prop('disabled',true).text(buttonTextWhileSearching);
         $form.find('#only-excel-hidden-input').val(onlyExcel);

         $("#attributesForm .attributeElement").each(function() {
            var name = $(this).attr('data-model');
            if(typeof name === 'undefined') return;
            var index = $(this).parent().index();
            var $hiddenOrder = $('<input class="hidden-remove" type="hidden" name="order_'+ name +'" value="'+ index+'" />');
            $form.append($hiddenOrder);
         });

         if(!onlyExcel) {  $('#results').html('');  }  // clears results div
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
             $('#results').html('<div style="color: red;">Server error during ajax request:'+error+'</div>');
           },
           success: function(data) {
             if(onlyExcel) {
                var $downloadForm = $('<form method="post" action="/secure/excel_generation"></form>');
                $downloadForm.appendTo('body').submit().remove();
             } else {
               $('#results').html(data.message);
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
                                $('<div id="'+ chartData.chartId+"-"+j.toString() +'"></div>').appendTo($chartDiv);
                                var chartJson = chartData.charts[j];
                                var chart = Highcharts.chart(chartData.chartId+"-"+j.toString(), chartJson);
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
             }
           }
         });

         // remove orderings
         $form.find('.hidden-remove').remove();

         return false;
     };

    $('#generate-reports-form').submit(function(e) {return submitFormFunction(e,false);});
    $('#generate-reports-form-button').click(function(e) {return submitFormFunction(e,false);});

    $('#download-to-excel-button').click(function(e) {return submitFormFunction(e,true);});

    // nested forms
    $('select.nested-filter-select').each(function() {
        $this = $(this);
        var displayItemSelectOptions = {width: '100%', placeholder: 'Search', closeOnSelect: true};
        $this.select2(displayItemSelectOptions);
    });

    $('select.nested-filter-select').on("change", function(e) {
        var $options = $(e.currentTarget.selectedOptions);
        var $hiddenOptions = $(e.currentTarget).find("option").not($options);
        $hiddenOptions.each(function(i,option){
            var id = $(option).val();
            var $draggable = $('.attributeElement[data-model="'+id+'"]');
            $draggable.find('input, select, textarea').prop('disabled', true).val(null).filter('.nested-filter-select').trigger('change');
            $draggable.find("div.attribute").addClass("disabled");
            $draggable.parent().hide();
            return true;
        });
        $options.each(function(i,option){
            var id = $(option).val();
            var $draggable = $('.attributeElement[data-model="'+id+'"]');
            $draggable.find('input, select, textarea').prop('disabled', false).filter('.nested-filter-select').trigger('change');
            $draggable.find("div.attribute").removeClass("disabled");
            $draggable.parent().show();
        });
        return true;
    });

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
                $attrSelect.val(values).trigger('change');
            }

            // handle any children
            if(childName != null) {
                var $childAttrSelect = $('#multiselect-nested-filter-select-'+parentName);
                if($parent.length > 0) {
                    // handle parent
                    var childValues = $childAttrSelect.val();
                    if(!childValues.includes(attrName)) {
                        childValues.push(attrName);
                        $childAttrSelect.val(childValues).trigger('change');
                    }
                }
            }
        }
    });

    $('.sidebar .nav-item .btn').click(function(e){
        $('.sidebar .nav-item .btn').removeClass('active');
        $(this).addClass('active');
    });

    $('.draggable .collapsible-header .remove-button').click(function(e) {
        e.stopPropagation();
        var $draggable = $(this).closest('.draggable');
        // get name
        var label = $draggable.attr('data-model');
        var $select = $draggable.closest('.nested-form-list').prev().find('select.nested-filter-select');
        var values = $select.val();
        var idx = $.inArray(label,values);
        if(idx >= 0) {
            values.splice(idx,1);
            $select.val(values).trigger('change');
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


    $('.single-select2').select2({
        minimumResultsForSearch: 5,
        width: "100%"
    });

    setCollapsibleHeaders(".collapsible-header");

    $('#main-content-id').tooltip({
        content: function() { return $(this).attr('title'); }
    });

    $('.nested-form-list').sortable();
    $('.nested-form-list').disableSelection();

    resetSearchForm();

    $('#main-content-id').addClass('show');

    $('#main-options-useHighlighter').click(function(e) {
        if($(this).is(":checked")) {
            $(this).val('on');
            $(this).prop('checked',true);
        } else {
            $(this).val('off');
            $(this).prop('checked',false);
        }
        return true;
    });

    $( ".datepicker" ).datepicker({
       changeMonth: true,
       changeYear: true,
       dateFormat: 'yy-mm-dd'
    });
});

var resetSearchForm = function() {
    $('.target .collapsible-header .remove-button').click();
    $('.draggable').find('select,textarea,input').prop("disabled",true).val(null).trigger('change');
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

var showTemplateFormHelper = function(formSelector,json) {
    var dataMap = jQuery.parseJSON(json);
    $.each(dataMap,function(id,value) {
        if(!id.startsWith("order_")) {
            var order = 0;
            if(dataMap.hasOwnProperty("order_"+id)) {
                order = dataMap["order_"+id];
            }

            var $elem = $('#'+id);
            var $draggable = $elem.closest(".attributeElement");
            $draggable.parent().show();
            $draggable.parent().attr("sort-order",order);
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
            $elem.trigger('change');
        }
    });
    $(formSelector+' .nested-form-list').each(function() {
        var list = $(this);
        var elems = list.children().filter('[sort-order]').detach();
        elems.sort(function(a,b) {
            var i = parseInt($(a).attr("sort-order"));
            var j = parseInt($(b).attr("sort-order"));
            return (i > j) ? 1 : (i < j) ? -1 : 0;
        });
        list.append(elems);
    });
    $(formSelector+' .nested-form-list').sortable('refreshPositions');
    $(formSelector+' select.nested-filter-select').trigger('change');
};

var showTemplateFunction = function(data){
    resetSearchForm();
    showTemplateFormHelper("#searchOptionsForm",data["searchoptionsmap"]);
    showTemplateFormHelper("#attributesForm",data["attributesmap"]);
    showTemplateFormHelper("#filtersForm",data["filtersmap"]);
    showTemplateFormHelper("#chartsForm",data["chartsmap"]);
    try {
        showTemplateFormHelper("#highlightForm",data["highlightmap"]);
    } catch(err) {

    }
    return false;
};


var loadEvent = function(){
 // do nothing :(
};
