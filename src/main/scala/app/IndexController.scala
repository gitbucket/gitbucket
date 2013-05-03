package app

class IndexController extends ControllerBase {
  
  get("/"){
    html.index()
  }

}