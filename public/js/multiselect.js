var expanded = false;

function showCheckboxes(var id) {
  var checkboxes = document.getElementById(id);
  if (!expanded) {
    checkboxes.style.display = "block";
    expanded = true;
  } else {
    checkboxes.style.display = "none";
    expanded = false;
  }
}